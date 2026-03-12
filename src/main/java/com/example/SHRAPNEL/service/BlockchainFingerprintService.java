package com.example.SHRAPNEL.service;

import com.example.SHRAPNEL.model.FileMetaData;
import com.example.SHRAPNEL.repository.FileMetaDataRepository;
import lombok.extern.slf4j.Slf4j;
import org.mlflow.tracking.MlflowClient;
import org.mlflow.api.proto.Service.RunInfo;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.RawTransactionManager;
import org.web3j.tx.TransactionManager;
import org.web3j.tx.response.PollingTransactionReceiptProcessor;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

/**
 * Service responsible for computing and recording an immutable fingerprint of an
 * encrypted file.  The SHA‑256 digest is pushed onto a Polygon (L2) network
 * using Web3j and the transaction parameters/metrics are logged to MLFlow so
 * that the cost of different gas configurations can be studied.  A very thin
 * Optuna helper (script) lives alongside this class and probes the same
 * service in order to tune the gas fee + thread‑pool settings.
 */
@Service
@Slf4j
public class BlockchainFingerprintService {

    private final Web3j web3j;
    private final Credentials credentials;
    private final FileMetaDataRepository repository;
    private final MlflowClient mlflow;
    private final String mlflowExperimentId;
    private final BigInteger defaultGasPrice;
    private final BigInteger defaultGasLimit;

    // --- production constructor ------------------------------------------------
    public BlockchainFingerprintService(Environment env,
                                        FileMetaDataRepository repository) {
        this.repository = repository;
        String provider = env.getProperty("shrapnel.blockchain.provider-url");
        String privateKey = env.getProperty("shrapnel.blockchain.private-key");
        this.defaultGasPrice = new BigInteger(env.getProperty("shrapnel.blockchain.default-gas-price","20000000000"));
        this.defaultGasLimit = new BigInteger(env.getProperty("shrapnel.blockchain.default-gas-limit","21000"));

        this.web3j = Web3j.build(new HttpService(provider));
        this.credentials = Credentials.create(privateKey);

        String trackingUri = env.getProperty("mlflow.tracking-uri");
        String experimentName = env.getProperty("mlflow.experiment-name","default");
        this.mlflow = new MlflowClient(trackingUri);
        this.mlflowExperimentId = mlflow.getExperimentByName(experimentName)
                .orElseGet(() -> mlflow.createExperiment(experimentName))
                .getExperimentId();
    }

    // --- additional constructor used for unit tests ---------------------------
    public BlockchainFingerprintService(Web3j web3j,
                                        Credentials credentials,
                                        MlflowClient mlflow,
                                        FileMetaDataRepository repository,
                                        String mlflowExperimentId,
                                        BigInteger defaultGasPrice,
                                        BigInteger defaultGasLimit) {
        this.web3j = web3j;
        this.credentials = credentials;
        this.mlflow = mlflow;
        this.repository = repository;
        this.mlflowExperimentId = mlflowExperimentId;
        this.defaultGasPrice = defaultGasPrice;
        this.defaultGasLimit = defaultGasLimit;
    }

    /**
     * Compute the SHA‑256 digest of a file.  Called immediately after
     * encryption; the resulting hex string ends up stored in the metadata and
     * is what we ship onto the blockchain.
     */
    public String computeSha256(Path path) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream is = Files.newInputStream(path)) {
            byte[] buffer = new byte[8192];
            int n;
            while ((n = is.read(buffer)) > 0) {
                digest.update(buffer, 0, n);
            }
        }
        return bytesToHex(digest.digest());
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * High‑level entry point invoked by upper layers once an encrypted file has
     * been written to disk.  It computes the fingerprint, pushes it to the
     * chain and persists the transaction hash in {@link FileMetaData} (which is
     * audited by Envers).  MLFlow parameters/metrics are logged under a single
     * run so that different runs can be compared later.
     */
    public void recordFingerprint(FileMetaData metadata, Path encryptedFile) {
        try {
            String sha = computeSha256(encryptedFile);
            metadata.setFileSha256(sha);

            long fileSize = Files.size(encryptedFile);

            // create new MLFlow run
            RunInfo runInfo = mlflow.createRun(mlflowExperimentId);
            String runId = runInfo.getRunUuid();
            mlflow.logParam(runId, "file_size", String.valueOf(fileSize));

            // parameters that could be tuned externally
            mlflow.logParam(runId, "gas_price", defaultGasPrice.toString());
            mlflow.logParam(runId, "gas_limit", defaultGasLimit.toString());

            // do the blockchain commit; this will block until receipt
            long start = System.nanoTime();
            TransactionReceipt receipt = commitHashToPolygon(sha, defaultGasPrice, defaultGasLimit);
            long durationMs = Duration.ofNanos(System.nanoTime() - start).toMillis();

            // post‑run metrics
            mlflow.logMetric(runId, "confirmation_time_ms", (double) durationMs);
            mlflow.logMetric(runId, "success", receipt.isStatusOK() ? 1.0 : 0.0);
            mlflow.setTerminated(runId);

            metadata.setBlockchainTxHash(receipt.getTransactionHash());
            repository.save(metadata);

            log.info("fingerprint recorded; tx={} sha={}", receipt.getTransactionHash(), sha);
        } catch (Exception e) {
            log.error("failed to record fingerprint", e);
        }
    }

    /**
     * Low‑level routine that builds and sends a simple transaction whose data
     * payload is the hexadecimal SHA string.  The recipient is the same as the
     * sender (no value is transferred); the goal is simply to stamp the
     * blockchain with the digest.  A virtual thread is used while waiting for
     * the confirmation to avoid blocking an OS thread.
     */
    TransactionReceipt commitHashToPolygon(String sha256,
                                           BigInteger gasPrice,
                                           BigInteger gasLimit) throws Exception {
        TransactionManager txManager = new RawTransactionManager(web3j, credentials);
        BigInteger nonce = web3j.ethGetTransactionCount(
                credentials.getAddress(), DefaultBlockParameterName.PENDING)
                .send().getTransactionCount();

        // the "to" address is simply our own address; data contains the hash
        String data = "0x" + sha256;
        String txHash = txManager.sendTransaction(gasPrice, gasLimit, credentials.getAddress(), data, BigInteger.ZERO)
                .getTransactionHash();

        PollingTransactionReceiptProcessor processor =
                new PollingTransactionReceiptProcessor(web3j, 1000, 15);

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            CompletableFuture<TransactionReceipt> future = CompletableFuture.supplyAsync(() -> {
                try {
                    return processor.waitForTransactionReceipt(txHash);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }, executor);
            return future.get();
        }
    }
}
