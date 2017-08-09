package net.corda.testing.contracts

import net.corda.core.contracts.*
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.NonEmptyList

// The dummy contract doesn't do anything useful. It exists for testing purposes.

val DUMMY_PROGRAM_ID = DummyContract()

data class DummyContract(override val legalContractReference: SecureHash = SecureHash.sha256("")) : Contract {
    interface State : ContractState {
        val magicNumber: Int
    }

    data class SingleOwnerState(override val magicNumber: Int = 0, override val owner: AbstractParty) : OwnableState, State {
        override val contract = DUMMY_PROGRAM_ID
        override val participants: List<AbstractParty>
            get() = listOf(owner)

        override fun withNewOwner(newOwner: AbstractParty) = CommandAndState(Commands.Move(), copy(owner = newOwner))
    }

    /**
     * Alternative state with multiple owners. This exists primarily to provide a dummy state with multiple
     * participants, and could in theory be merged with [SingleOwnerState] by putting the additional participants
     * in a different field, however this is a good example of a contract with multiple states.
     */
    data class MultiOwnerState(override val magicNumber: Int = 0,
                               override val participants: List<AbstractParty>) : DummyContract.State {
        override val contract = DUMMY_PROGRAM_ID
    }

    interface Commands : CommandData {
        class Create : TypeOnlyCommandData(), Commands
        class Move : TypeOnlyCommandData(), Commands
    }

    override fun verify(tx: LedgerTransaction) {
        // Always accepts.
    }

    companion object {
        @JvmStatic
        fun generateInitial(magicNumber: Int, notary: Party, owners: NonEmptyList<PartyAndReference>): TransactionBuilder {
            return if (owners.size == 1) {
                val state = SingleOwnerState(magicNumber, owners[0].party)
                TransactionBuilder(notary).withItems(state, Command(Commands.Create(), owners[0].party.owningKey))
            } else {
                val state = MultiOwnerState(magicNumber, owners.map { it.party })
                TransactionBuilder(notary).withItems(state, Command(Commands.Create(), owners.map { it.party.owningKey }))
            }
        }

        fun move(prior: StateAndRef<SingleOwnerState>, newOwner: AbstractParty) = move(NonEmptyList.of(prior), newOwner)
        fun move(priors: NonEmptyList<StateAndRef<SingleOwnerState>>, newOwner: AbstractParty): TransactionBuilder {
            val priorState = priors[0].state.data
            val (cmd, state) = priorState.withNewOwner(newOwner)
            return TransactionBuilder(notary = priors[0].state.notary).withItems(
                    /* INPUTS  */ *priors.toTypedArray(),
                    /* COMMAND */ Command(cmd, priorState.owner.owningKey),
                    /* OUTPUT  */ state
            )
        }
    }
}
