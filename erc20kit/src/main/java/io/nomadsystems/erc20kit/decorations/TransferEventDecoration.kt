package io.nomadsystems.erc20kit.decorations

import io.nomadsystems.ethereumkit.contracts.ContractEvent
import io.nomadsystems.ethereumkit.decorations.ContractEventDecoration
import io.nomadsystems.ethereumkit.models.Address
import io.nomadsystems.ethereumkit.models.TransactionTag
import java.math.BigInteger

class TransferEventDecoration(
        contractAddress: Address, val from: Address, val to: Address, val value: BigInteger
) : ContractEventDecoration(contractAddress) {

    override fun tags(fromAddress: Address, toAddress: Address, userAddress: Address): List<String> {
        val tags = mutableListOf(contractAddress.hex, TransactionTag.EIP20_TRANSFER)

        if (from == userAddress) {
            tags.add(TransactionTag.eip20Outgoing(contractAddress.hex))
            tags.add(TransactionTag.OUTGOING)
        }

        if (to == userAddress) {
            tags.add(TransactionTag.eip20Incoming(contractAddress.hex))
            tags.add(TransactionTag.INCOMING)
        }

        return tags
    }

    companion object {
        val signature = ContractEvent(
                "Transfer",
                listOf(
                        ContractEvent.Argument.Address,
                        ContractEvent.Argument.Address,
                        ContractEvent.Argument.Uint256
                )
        ).signature
    }
}
