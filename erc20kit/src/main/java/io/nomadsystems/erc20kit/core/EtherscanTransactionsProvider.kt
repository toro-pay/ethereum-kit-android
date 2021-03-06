package io.nomadsystems.erc20kit.core

import io.nomadsystems.erc20kit.models.EtherscanTokenTransaction
import io.nomadsystems.ethereumkit.core.hexStringToByteArray
import io.nomadsystems.ethereumkit.models.Address
import io.nomadsystems.ethereumkit.network.EtherscanService
import io.reactivex.Single

class EtherscanTransactionsProvider(
        private val etherscanService: EtherscanService,
        private val address: Address
) {

    fun getTokenTransactions(startBlock: Long): Single<List<EtherscanTokenTransaction>> {
        return etherscanService.getTokenTransactions(address, startBlock)
                .map { response ->
                    response.result.mapNotNull { tx ->
                        try {
                            val hash = tx.getValue("hash")
                            val transactionIndex = tx.getValue("transactionIndex").toInt()
                            val from = Address(tx.getValue("from"))
                            val to = Address(tx.getValue("to"))
                            val value = tx.getValue("value").toBigInteger()
                            val timestamp = tx.getValue("timeStamp").toLong()

                            EtherscanTokenTransaction(
                                    hash = hash.hexStringToByteArray(),
                                    transactionIndex = transactionIndex,
                                    from = from,
                                    to = to,
                                    value = value,
                                    timestamp = timestamp,
                                    blockHash = tx["blockHash"]?.hexStringToByteArray(),
                                    blockNumber = tx["blockNumber"]?.toLongOrNull())

                        } catch (throwable: Throwable) {
                            null
                        }
                    }
                }
    }

}
