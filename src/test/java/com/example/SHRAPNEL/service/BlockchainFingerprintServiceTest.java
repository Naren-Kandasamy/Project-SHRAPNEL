package com.example.SHRAPNEL.service;

import com.example.SHRAPNEL.model.FileMetaData;
import com.example.SHRAPNEL.repository.FileMetaDataRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mlflow.tracking.MlflowClient;
import org.web3j.protocol.Web3j;
import org.web3j.crypto.Credentials;

import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class BlockchainFingerprintServiceTest {

    @Mock
    private FileMetaDataRepository repo;
    @Mock
    private Web3j web3j;
    @Mock
    private Credentials credentials;
    @Mock
    private MlflowClient mlflow;

    private BlockchainFingerprintService service;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        service = new BlockchainFingerprintService(web3j,
                credentials,
                mlflow,
                repo,
                "exp-id",
                BigInteger.valueOf(123L),
                BigInteger.valueOf(21000L));
    }

    @Test
    void computeSha256_knownContents() throws Exception {
        Path temp = Files.createTempFile("shrapnel-test", ".bin");
        Files.writeString(temp, "hello world");
        String digest = service.computeSha256(temp);

        // SHA-256 of "hello world" computed externally
        assertThat(digest).isEqualTo("b94d27b9934d3e08a52e52d7da7dabfa" +
                "c484efe37a5380ee9088f7ace2efcde9");
    }

    @Test
    void recordFingerprint_invokesRepositoryWithTxHash() throws Exception {
        FileMetaData meta = new FileMetaData("foo.txt", 5L);
        Path temp = Files.createTempFile("test2", ".txt");
        Files.writeString(temp, "payload");

        // spy service so that the blockchain call returns a fixed receipt
        BlockchainFingerprintService spy = spy(service);
        doReturn(new org.web3j.protocol.core.methods.response.TransactionReceipt())
                .when(spy)
                .commitHashToPolygon(anyString(), any(), any());

        spy.recordFingerprint(meta, temp);

        ArgumentCaptor<FileMetaData> captor = ArgumentCaptor.forClass(FileMetaData.class);
        verify(repo).save(captor.capture());
        FileMetaData saved = captor.getValue();

        assertThat(saved.getFileSha256()).isNotNull();
        // transaction hash may be empty as the fake receipt hasn't been populated
        assertThat(saved.getBlockchainTxHash()).isNotNull();

        // ensure we did attempt to push the computed hash onto chain
        verify(spy).commitHashToPolygon(eq(saved.getFileSha256()), any(), any());

        // mlflow should have been invoked with the basic parameters/metrics
        verify(mlflow).logParam(anyString(), eq("file_size"), anyString());
        verify(mlflow).logParam(anyString(), eq("gas_price"), anyString());
        verify(mlflow).logParam(anyString(), eq("gas_limit"), anyString());
        verify(mlflow).logMetric(anyString(), eq("confirmation_time_ms"), anyDouble());
        verify(mlflow).logMetric(anyString(), eq("success"), anyDouble());
        verify(mlflow).setTerminated(anyString());
    }
}