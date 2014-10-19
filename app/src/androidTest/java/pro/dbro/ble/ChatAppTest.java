package pro.dbro.ble;

import android.app.Application;
import android.test.ApplicationTestCase;

import java.util.Arrays;
import java.util.Date;

import pro.dbro.ble.crypto.SodiumShaker;
import pro.dbro.ble.model.ChatContentProvider;
import pro.dbro.ble.model.MessageTable;
import pro.dbro.ble.model.Peer;
import pro.dbro.ble.model.PeerTable;
import pro.dbro.ble.protocol.ChatProtocol;
import pro.dbro.ble.protocol.Identity;
import pro.dbro.ble.protocol.Message;
import pro.dbro.ble.protocol.OwnedIdentity;
import pro.dbro.ble.util.RandomString;

/**
 * Tests of the ChatProtocol and Chat Application.
 */
public class ChatAppTest extends ApplicationTestCase<Application> {
    public ChatAppTest() {
        super(Application.class);
    }

    OwnedIdentity mSenderIdentity;

    protected void setUp() throws Exception {
        super.setUp();

        String username = new RandomString(ChatProtocol.ALIAS_LENGTH).nextString();
        mSenderIdentity = SodiumShaker.generateOwnedIdentityForAlias(username);
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }

    /** Protocol Tests **/

    /**
     * {@link pro.dbro.ble.protocol.Identity} -> byte[] -> {@link pro.dbro.ble.protocol.Identity}
     */
    public void testCreateAndConsumeIdentityResponse() {
        byte[] identityResponse = ChatProtocol.createIdentityResponse(mSenderIdentity);

        // Parse Identity from sender's identityResponse response byte[]
        Identity parsedIdentity = ChatProtocol.consumeIdentityResponse(identityResponse);

        assertEquals(parsedIdentity.alias, mSenderIdentity.alias);
        assertEquals(Arrays.equals(parsedIdentity.publicKey, mSenderIdentity.publicKey), true);
        assertDateIsRecent(parsedIdentity.dateSeen);
    }

    /**
     * {@link pro.dbro.ble.protocol.Message} -> byte[] -> {@link pro.dbro.ble.protocol.Message}
     */
    public void testCreateAndConsumeMessageResponse() {
        String messageBody = new RandomString(ChatProtocol.MESSAGE_BODY_LENGTH).nextString();

        byte[] messageResponse = ChatProtocol.createPublicMessageResponse(mSenderIdentity, messageBody);

        Message parsedMessage = ChatProtocol.consumeMessageResponse(messageResponse);

        assertEquals(messageBody, parsedMessage.body);
        assertEquals(Arrays.equals(parsedMessage.sender.publicKey, mSenderIdentity.publicKey), true);
        assertDateIsRecent(parsedMessage.authoredDate);
    }

    /** Application Tests **/

    /**
     * Create a {@link pro.dbro.ble.model.Peer} for protocol {@link pro.dbro.ble.protocol.Identity},
     * then create a {@link pro.dbro.ble.model.Message} for protocol {@link pro.dbro.ble.protocol.Message}.
     */
    public void testApplicationIdentityCreation() {
        // Get or create new primary identity. This Identity serves as the app user
        Peer user = ChatApp.getPrimaryIdentity(getContext());
        boolean createdNewUser = false;

        if (user == null) {
            createdNewUser = true;
            int userId = ChatApp.createNewIdentity(getContext(), new RandomString(ChatProtocol.ALIAS_LENGTH).nextString());
            user = ChatApp.getPrimaryIdentity(getContext());

            assertEquals(userId, user.getId());
        }

        // User discovers a peer
        Peer remotePeer = ChatApp.consumeReceivedIdentity(getContext(), ChatProtocol.createIdentityResponse(mSenderIdentity));
        // Assert Identity response parsed successfully
        assertEquals(Arrays.equals(remotePeer.getKeyPair().publicKey, mSenderIdentity.publicKey), true);

        // Craft a mock message from remote peer
        String mockReceivedMessageBody = new RandomString(ChatProtocol.MESSAGE_BODY_LENGTH).nextString();
        byte[] mockReceivedMessage = ChatProtocol.createPublicMessageResponse(mSenderIdentity, mockReceivedMessageBody);

        // User receives mock message from remote peer
        pro.dbro.ble.model.Message parsedMockReceivedMessage = ChatApp.consumeReceivedBroadcastMessage(getContext(), mockReceivedMessage);
        assertEquals(mockReceivedMessageBody.equals(parsedMockReceivedMessage.getBody()), true);

        // Cleanup
        // TODO: Should mock database
        if (createdNewUser) {
            int numDeleted;
            numDeleted = getContext().getContentResolver().delete(ChatContentProvider.Peers.PEERS,
                    PeerTable.id + " = ?",
                    new String[] {String.valueOf(user.getId())});

            assertEquals(numDeleted, 1);
            numDeleted = 0;

            numDeleted = getContext().getContentResolver().delete(ChatContentProvider.Peers.PEERS,
                    PeerTable.id + " = ?",
                    new String[] {String.valueOf(remotePeer.getId())});

            assertEquals(numDeleted, 1);
            numDeleted = 0;

            numDeleted = getContext().getContentResolver().delete(ChatContentProvider.Messages.MESSAGES,
                    MessageTable.id + " = ?",
                    new String[] {String.valueOf(parsedMockReceivedMessage.getId())});
            assertEquals(numDeleted, 1);
            numDeleted = 0;
        }

    }

    private void assertDateIsRecent(Date mustBeRecent) {
        long now = new Date().getTime();
        long oneSecondAgo = now - 1000;

        if ( (mustBeRecent.getTime() > now) ){
            throw new IllegalStateException("Parsed Identity time is from the future " + mustBeRecent);

        } else if (mustBeRecent.getTime() < oneSecondAgo) {
            throw new IllegalStateException("Parsed Identity time is from more than 500ms ago " + mustBeRecent);
        }
    }
}