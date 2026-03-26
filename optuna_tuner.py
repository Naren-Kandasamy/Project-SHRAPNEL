"""Small script that exercises the Polygon commit step and lets Optuna
search for a low‑cost configuration.

The script assumes the service described in ``BlockchainFingerprintService``
is reachable over HTTP (e.g. the local server) and exposes an endpoint that
accepts JSON containing the gas parameters and returns the transaction receipt
(or at least a cost estimate).  For simplicity we talk directly to Web3 using
web3.py; the aim is to demonstrate how the search space can be described.  A
real deployment might call into the Java application via REST instead.
"""

import os
import time

import optuna
from web3 import Web3

PROVIDER = os.getenv("PROVIDER_URL", "https://sepolia.infura.io/v3/your-api-key")
PRIVATE_KEY = os.getenv("PRIVATE_KEY")

w3 = Web3(Web3.HTTPProvider(PROVIDER))
account = w3.eth.account.from_key(PRIVATE_KEY) if PRIVATE_KEY else None


def send_dummy_tx(gas_price: int, thread_pool: int):
    # create a minimal tx that just sends 0 wei to ourselves with some data
    nonce = w3.eth.get_transaction_count(account.address)
    tx = {
        "to": account.address,
        "value": 0,
        "gas": 21000,
        "maxPriorityFeePerGas": gas_price,
        # set a sufficiently high maxFeePerGas so tx isn't dropped
        "maxFeePerGas": gas_price + 1_000_000_000,
        "nonce": nonce,
        "data": b"\x00",
    }
    signed = account.sign_transaction(tx)
    start = time.time()
    tx_hash = w3.eth.send_raw_transaction(signed.rawTransaction)
    receipt = w3.eth.wait_for_transaction_receipt(tx_hash)
    duration = time.time() - start
    cost = receipt.gasUsed * gas_price
    return cost, duration


def objective(trial: optuna.trial.Trial):
    max_priority = trial.suggest_int("maxPriorityFeePerGas", 1, 100 * 10**9)
    pool = trial.suggest_int("threadPoolSize", 1, 32)
    cost, latency = send_dummy_tx(max_priority, pool)
    trial.set_user_attr("confirmation_seconds", latency)
    return cost


if __name__ == "__main__":
    study = optuna.create_study(direction="minimize")
    study.optimize(objective, n_trials=20)
    print("===== Best trial =====")
    print(study.best_trial)
    print("Parameters:", study.best_trial.params)
    print("Cost reduction achieved:", study.best_trial.value)
