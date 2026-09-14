/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @immat0x1, 2023

*/

package com.exteragram.messenger.utils;

import android.text.SpannableStringBuilder;
import android.text.TextUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.RequestDelegate;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.TranscribeButton;

import java.io.File;
import java.util.Locale;

public class ChatUtils {

    public static String getDC(TLRPC.User user) {
        return getDC(user, null);
    }

    public static String getDC(TLRPC.Chat chat) {
        return getDC(null, chat);
    }

    public static String getDC(TLRPC.User user, TLRPC.Chat chat) {
        try {
            var manager = getConnectionsManager();
            if (manager == null) {
                return getDCName(0);
            }
            int DC = 0, myDC = manager.getCurrentDatacenterId();
            if (user != null) {
                if (UserObject.isUserSelf(user) && myDC != -1) {
                    DC = myDC;
                } else {
                    DC = user.photo != null ? user.photo.dc_id : -1;
                }
            } else if (chat != null) {
                DC = chat.photo != null ? chat.photo.dc_id : -1;
            }
            if (DC == -1 || DC == 0) {
                return getDCName(0);
            } else {
                return String.format(Locale.ROOT, "DC%d, %s", DC, getDCName(DC));
            }
        } catch (Exception e) {
            return getDCName(0);
        }
    }

    public static String getDCName(int dc) {
        switch (dc) {
            case 1:
            case 3:
                return "Miami FL, USA";
            case 2:
            case 4:
                return "Amsterdam, NL";
            case 5:
                return "Singapore, SG";
            default:
                return null;
        }
    }

    public static boolean isSubscribedTo(long id) {
        var controller = getMessagesController();
        if (controller == null) {
            return false;
        }
        TLRPC.Chat chat = controller.getChat(id);
        return chat != null && !chat.left && !chat.kicked;
    }

    public static String getName(long did) {
        try {
            int currentAccount = UserConfig.selectedAccount;
            var controller = getMessagesController();
            if (controller == null) {
                return null;
            }
            String name = null;
            if (DialogObject.isEncryptedDialog(did)) {
                TLRPC.EncryptedChat encryptedChat = controller.getEncryptedChat(DialogObject.getEncryptedChatId(did));
                if (encryptedChat != null) {
                    TLRPC.User user = controller.getUser(encryptedChat.user_id);
                    if (user != null)
                        name = ContactsController.formatName(user.first_name, user.last_name);
                }
            } else if (DialogObject.isUserDialog(did)) {
                TLRPC.User user = controller.getUser(did);
                if (user != null) name = ContactsController.formatName(user.first_name, user.last_name);
            } else {
                TLRPC.Chat chat = controller.getChat(-did);
                if (chat != null) name = chat.title;
            }
            var config = UserConfig.getInstance(currentAccount);
            if (config == null) {
                return name;
            }
            return did == config.getClientUserId() ? LocaleController.getString("SavedMessages", R.string.SavedMessages) : name;
        } catch (Exception e) {
            return null;
        }
    }

    public interface SearchCallback {
        void run(TLRPC.User user);
    }

    public static void searchById(Long userId, SearchCallback callback) {
        searchById(userId, callback, false);
    }

    private static void searchById(Long userId, SearchCallback callback, boolean fallbackUsed) {
        if (userId == null || userId == 0) {
            if (callback != null) {
                callback.run(null);
            }
            return;
        }
        if (callback == null) {
            return;
        }
        var controller = getMessagesController();
        if (controller == null) {
            callback.run(null);
            return;
        }
        TLRPC.User user = controller.getUser(userId);
        if (user != null) {
            callback.run(user);
        } else {
            final boolean used = fallbackUsed;
            searchUser(userId, true, true, user1 -> {
                if (user1 != null && user1.access_hash != 0) {
                    callback.run(user1);
                } else {
                    if (!used) {
                        searchById(0x100000000L + userId, callback, true);
                    } else {
                        callback.run(null);
                    }
                }
            });
        }
    }

    private static void searchUser(long userId, boolean searchUser, boolean cache, SearchCallback callback) {
        if (callback == null) {
            return;
        }
        try {
            var controller = getMessagesController();
            if (controller == null) {
                callback.run(null);
                return;
            }
            final long bot_id = 1696868284L;
            TLRPC.User bot = controller.getUser(bot_id);
            if (bot == null) {
                if (searchUser) {
                    resolveUser("tgdb_bot", bot_id, user -> searchUser(userId, false, false, callback));
                } else {
                    callback.run(null);
                }
                return;
            }

            String key = "user_search_" + userId;
            RequestDelegate requestDelegate = (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                try {
                    if (callback == null) {
                        return;
                    }
                    if (cache && (!(response instanceof TLRPC.messages_BotResults) || ((TLRPC.messages_BotResults) response).results == null || ((TLRPC.messages_BotResults) response).results.isEmpty())) {
                        searchUser(userId, searchUser, false, callback);
                        return;
                    }

                    if (response instanceof TLRPC.messages_BotResults) {
                        TLRPC.messages_BotResults res = (TLRPC.messages_BotResults) response;
                        if (res.results == null) {
                            callback.run(null);
                            return;
                        }
                        if (!cache && res.cache_time != 0) {
                            var storage = getMessageStorage();
                            if (storage != null) {
                                storage.saveBotCache(key, res);
                            }
                        }
                        if (res.results.isEmpty()) {
                            callback.run(null);
                            return;
                        }
                        TLRPC.BotInlineResult result = res.results.get(0);
                        if (result == null || result.send_message == null || TextUtils.isEmpty(result.send_message.message)) {
                            callback.run(null);
                            return;
                        }
                        String[] lines = result.send_message.message.split("\n");
                        if (lines.length < 3) {
                            callback.run(null);
                            return;
                        }
                        var user1 = new TLRPC.TL_user();
                        for (String line : lines) {
                            if (line == null) {
                                continue;
                            }
                            line = line.replaceAll("\\p{C}", "").trim();
                            if (line.startsWith("\uD83C\uDD94")) {
                                user1.id = Utilities.parseLong(line.replaceAll("\\D+", "").trim());
                            } else if (line.startsWith("\uD83D\uDCE7") && line.contains("@")) {
                                user1.username = line.substring(line.indexOf('@') + 1).trim();
                            }
                        }
                        if (user1.id == 0) {
                            callback.run(null);
                            return;
                        }
                        if (user1.username != null) {
                            resolveUser(user1.username, user1.id, user -> {
                                if (user != null) {
                                    callback.run(user);
                                } else {
                                    user1.username = null;
                                    callback.run(user1);
                                }
                            });
                        } else {
                            callback.run(user1);
                        }
                    } else {
                        callback.run(null);
                    }
                } catch (Exception e) {
                    try {
                        callback.run(null);
                    } catch (Exception ignored) {
                    }
                }
            });

            if (cache) {
                var storage = getMessageStorage();
                if (storage != null) {
                    storage.getBotCache(key, requestDelegate);
                } else {
                    searchUser(userId, searchUser, false, callback);
                }
            } else {
                var connection = getConnectionsManager();
                if (connection == null) {
                    callback.run(null);
                    return;
                }
                TLRPC.TL_messages_getInlineBotResults req = new TLRPC.TL_messages_getInlineBotResults();
                req.query = String.valueOf(userId);
                req.bot = controller.getInputUser(bot);
                req.offset = "";
                req.peer = new TLRPC.TL_inputPeerEmpty();
                connection.sendRequest(req, requestDelegate, ConnectionsManager.RequestFlagFailOnServerErrors);
            }
        } catch (Exception e) {
            try {
                callback.run(null);
            } catch (Exception ignored) {
            }
        }
    }

    private static void resolveUser(String userName, long userId, SearchCallback callback) {
        if (callback == null || userName == null) {
            if (callback != null) {
                try {
                    callback.run(null);
                } catch (Exception ignored) {
                }
            }
            return;
        }
        try {
            var connection = getConnectionsManager();
            var controller = getMessagesController();
            var storage = getMessageStorage();
            if (connection == null || controller == null) {
                callback.run(null);
                return;
            }
            TLRPC.TL_contacts_resolveUsername req = new TLRPC.TL_contacts_resolveUsername();
            req.username = userName;
            connection.sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                try {
                    if (response instanceof TLRPC.TL_contacts_resolvedPeer) {
                        TLRPC.TL_contacts_resolvedPeer res = (TLRPC.TL_contacts_resolvedPeer) response;
                        if (res.users != null) {
                            controller.putUsers(res.users, false);
                        }
                        if (res.chats != null) {
                            controller.putChats(res.chats, false);
                        }
                        if (storage != null && res.users != null && res.chats != null) {
                            storage.putUsersAndChats(res.users, res.chats, true, true);
                        }
                        if (res.peer instanceof TLRPC.TL_peerUser && res.peer.user_id == userId) {
                            callback.run(controller.getUser(userId));
                        } else {
                            callback.run(null);
                        }
                    } else {
                        callback.run(null);
                    }
                } catch (Exception e) {
                    try {
                        callback.run(null);
                    } catch (Exception ignored) {
                    }
                }
            }));
        } catch (Exception e) {
            try {
                callback.run(null);
            } catch (Exception ignored) {
            }
        }
    }

    public static String getOwnerIds(long stickerSetId) {
        return "int32: " + (stickerSetId >> 32) + '\n' +
                "int64: " + (0x100000000L + (stickerSetId >> 32));
    }

    public static MessagesController getMessagesController() {
        return MessagesController.getInstance(UserConfig.selectedAccount);
    }

    public static MessagesStorage getMessageStorage() {
        return MessagesStorage.getInstance(UserConfig.selectedAccount);
    }

    public static ConnectionsManager getConnectionsManager() {
        return ConnectionsManager.getInstance(UserConfig.selectedAccount);
    }

    public static FileLoader getFileLoader() {
        return FileLoader.getInstance(UserConfig.selectedAccount);
    }

    public static void addMessageToClipboard(MessageObject selectedObject, Runnable callback) {
        if (selectedObject == null) {
            return;
        }
        try {
            String path = getPathToMessage(selectedObject);
            if (!TextUtils.isEmpty(path)) {
                SystemUtils.addFileToClipboard(new File(path), callback);
            } else if (callback != null) {
                try {
                    callback.run();
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
            if (callback != null) {
                try {
                    callback.run();
                } catch (Exception ignored2) {
                }
            }
        }
    }

    public static String getPathToMessage(MessageObject messageObject) {
        if (messageObject == null || messageObject.messageOwner == null) {
            return null;
        }
        try {
            String path = messageObject.messageOwner.attachPath;
            if (!TextUtils.isEmpty(path)) {
                File temp = new File(path);
                if (!temp.exists()) {
                    path = null;
                }
            }
            var loader = getFileLoader();
            if (loader == null) {
                return path;
            }
            if (TextUtils.isEmpty(path)) {
                File f = loader.getPathToMessage(messageObject.messageOwner);
                path = f != null ? f.toString() : null;
                if (!TextUtils.isEmpty(path)) {
                    File temp = new File(path);
                    if (!temp.exists()) {
                        path = null;
                    }
                }
            }
            if (TextUtils.isEmpty(path)) {
                File f = loader.getPathToAttach(messageObject.getDocument(), true);
                path = f != null ? f.toString() : null;
                if (TextUtils.isEmpty(path)) {
                    return null;
                }
                File temp = new File(path);
                if (!temp.exists()) {
                    return null;
                }
            }
            return path;
        } catch (Exception e) {
            return null;
        }
    }

    public static boolean hasArchivedChats() {
        try {
            var controller = getMessagesController();
            return controller != null && controller.dialogs_dict != null && controller.dialogs_dict.get(DialogObject.makeFolderDialogId(1)) != null;
        } catch (Exception e) {
            return false;
        }
    }

    public static CharSequence getMessageText(MessageObject selectedObject, MessageObject.GroupedMessages selectedObjectGroup) {
        if (selectedObject == null || selectedObject.messageOwner == null) {
            return null;
        }
        CharSequence messageTextToTranslate = null;
        if (selectedObject.type != MessageObject.TYPE_EMOJIS && selectedObject.type != MessageObject.TYPE_ANIMATED_STICKER && selectedObject.type != MessageObject.TYPE_STICKER) {
            messageTextToTranslate = getMessageCaption(selectedObject, selectedObjectGroup);
            if (messageTextToTranslate == null && selectedObject.isPoll()) {
                try {
                    TLRPC.Poll poll = ((TLRPC.TL_messageMediaPoll) selectedObject.messageOwner.media).poll;
                    StringBuilder pollText = new StringBuilder(poll.question).append("\n");
                    for (TLRPC.TL_pollAnswer answer : poll.answers)
                        pollText.append("\n\uD83D\uDD18 ").append(answer.text);
                    messageTextToTranslate = pollText.toString();
                } catch (Exception ignored) {
                }
            }
            if (messageTextToTranslate == null && MessageObject.isMediaEmpty(selectedObject.messageOwner)) {
                messageTextToTranslate = getMessageContent(selectedObject);
            }
            if (messageTextToTranslate != null && Emoji.fullyConsistsOfEmojis(messageTextToTranslate)) {
                messageTextToTranslate = null;
            }
        }
        if (selectedObject.translated || selectedObject.isRestrictedMessage) {
            messageTextToTranslate = null;
        }
        return messageTextToTranslate;
    }

    private static CharSequence getMessageCaption(MessageObject messageObject, MessageObject.GroupedMessages group) {
        String restrictionReason = MessagesController.getRestrictionReason(messageObject.messageOwner.restriction_reason);
        if (!TextUtils.isEmpty(restrictionReason)) {
            return restrictionReason;
        }
        if (messageObject.isVoiceTranscriptionOpen() && !TranscribeButton.isTranscribing(messageObject)) {
            return messageObject.getVoiceTranscription();
        }
        if (messageObject.caption != null) {
            return messageObject.caption;
        }
        if (group == null) {
            return null;
        }
        CharSequence caption = null;
        for (int a = 0, N = group.messages.size(); a < N; a++) {
            MessageObject message = group.messages.get(a);
            if (message.caption != null) {
                if (caption != null) {
                    return null;
                }
                caption = message.caption;
            }
        }
        return caption;
    }

    private static CharSequence getMessageContent(MessageObject messageObject) {
        SpannableStringBuilder str = new SpannableStringBuilder();
        String restrictionReason = MessagesController.getRestrictionReason(messageObject.messageOwner.restriction_reason);
        if (!TextUtils.isEmpty(restrictionReason)) {
            str.append(restrictionReason);
        } else if (messageObject.caption != null) {
            str.append(messageObject.caption);
        } else {
            str.append(messageObject.messageText);
        }
        return str.toString();
    }
}
