package io.nomadwallet.erc20kit.core

import android.content.Context
import io.nomadsystems.erc20kit.contract.Eip20ContractMethodFactories
import io.nomadsystems.ethereumkit.core.EthereumKit
import io.nomadsystems.ethereumkit.core.EthereumKit.SyncState
import io.nomadsystems.ethereumkit.core.IDecorator
import io.nomadsystems.ethereumkit.core.ITransactionSyncer
import io.nomadsystems.ethereumkit.models.Address
import io.nomadsystems.ethereumkit.models.DefaultBlockParameter
import io.nomadsystems.ethereumkit.models.FullTransaction
import io.nomadsystems.ethereumkit.models.TransactionData
import io.reactivex.BackpressureStrategy
import io.reactivex.Flowable
import io.reactivex.Single
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import java.math.BigInteger

class Erc20Kit(
        private val ethereumKit: EthereumKit,
        private val transactionManager: TransactionManager,
        private val balanceManager: IBalanceManager,
        private val allowanceManager: AllowanceManager,
        private val state: KitState = KitState()
) : IBalanceManagerListener {

    private val disposables = CompositeDisposable()

    init {
        onSyncStateUpdate(ethereumKit.syncState)
        state.balance = balanceManager.balance

        ethereumKit.syncStateFlowable
                .subscribe {
                    onSyncStateUpdate(it)
                }.let {
                    disposables.add(it)
                }

        transactionManager.transactionsAsync
                .subscribeOn(Schedulers.io())
                .subscribe {
                    balanceManager.sync()
                }.let {
                    disposables.add(it)
                }
    }

    val syncState: SyncState
        get() = state.syncState

    val syncStateFlowable: Flowable<SyncState>
        get() = state.syncStateSubject.toFlowable(BackpressureStrategy.LATEST)

    val transactionsSyncState: SyncState
        get() = ethereumKit.transactionsSyncState

    val transactionsSyncStateFlowable: Flowable<SyncState>
        get() = ethereumKit.transactionsSyncStateFlowable

    val balance: BigInteger?
        get() = state.balance

    val balanceFlowable: Flowable<BigInteger>
        get() = state.balanceSubject.toFlowable(BackpressureStrategy.LATEST)

    val transactionsFlowable: Flowable<List<FullTransaction>>
        get() = transactionManager.transactionsAsync

    fun start() {
        balanceManager.sync()
    }

    fun stop() {
        transactionManager.stop()

        disposables.clear()
    }

    fun refresh() {}

    fun getAllowanceAsync(spenderAddress: Address, defaultBlockParameter: DefaultBlockParameter = DefaultBlockParameter.Latest): Single<BigInteger> {
        return allowanceManager.allowance(spenderAddress, defaultBlockParameter)
    }

    fun buildApproveTransactionData(spenderAddress: Address, amount: BigInteger): TransactionData {
        return allowanceManager.approveTransactionData(spenderAddress, amount)
    }

    fun buildTransferTransactionData(to: Address, value: BigInteger): TransactionData {
        return transactionManager.buildTransferTransactionData(to, value)
    }

    fun getTransactionsAsync(fromHash: ByteArray?, limit: Int?): Single<List<FullTransaction>> {
        return transactionManager.getTransactionsAsync(fromHash, limit)
    }

    fun getPendingTransactions(): List<FullTransaction> {
        return transactionManager.getPendingTransactions()
    }

    //region IBalanceManagerListener
    override fun onSyncBalanceSuccess(balance: BigInteger) {
        state.balance = balance
        state.syncState = SyncState.Synced()
    }

    override fun onSyncBalanceError(error: Throwable) {
        state.syncState = SyncState.NotSynced(error)
    }
    //endregion

    private fun onSyncStateUpdate(syncState: SyncState) {
        when (syncState) {
            is SyncState.NotSynced -> state.syncState = SyncState.NotSynced(syncState.error)
            is SyncState.Syncing -> state.syncState = SyncState.Syncing()
            is SyncState.Synced -> {
                state.syncState = SyncState.Syncing()
                balanceManager.sync()
            }
        }
    }

    companion object {

        fun getInstance(
                context: Context,
                ethereumKit: EthereumKit,
                contractAddress: Address
        ): Erc20Kit {

            val address = ethereumKit.receiveAddress

            val erc20KitDatabase = Erc20DatabaseManager.getErc20Database(context, ethereumKit.networkType, ethereumKit.walletId, contractAddress)
            val roomStorage = Erc20Storage(erc20KitDatabase)
            val balanceStorage: ITokenBalanceStorage = roomStorage

            val dataProvider: IDataProvider = DataProvider(ethereumKit)
            val transactionManager = TransactionManager(contractAddress, ethereumKit)
            val balanceManager: IBalanceManager = BalanceManager(contractAddress, address, balanceStorage, dataProvider)
            val allowanceManager = AllowanceManager(ethereumKit, contractAddress, address)

            val erc20Kit = Erc20Kit(ethereumKit, transactionManager, balanceManager, allowanceManager)

            balanceManager.listener = erc20Kit

            return erc20Kit
        }

        fun addTransactionSyncer(ethereumKit: EthereumKit) {
            val transactionsProvider = EtherscanTransactionsProvider(ethereumKit.etherscanService, ethereumKit.receiveAddress)
            ethereumKit.addTransactionSyncer(Erc20TransactionSyncer(transactionsProvider))
        }

        fun addDecorator(ethereumKit: EthereumKit) {
            val decorator = Eip20TransactionDecorator(ethereumKit.receiveAddress, Eip20ContractMethodFactories)
            ethereumKit.addDecorator(decorator)
        }

        fun clear(context: Context, networkType: EthereumKit.NetworkType, walletId: String) {
            Erc20DatabaseManager.clear(context, networkType, walletId)
        }
    }

}