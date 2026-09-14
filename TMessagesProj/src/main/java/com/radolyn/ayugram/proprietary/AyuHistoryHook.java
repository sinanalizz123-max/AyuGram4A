package com.radolyn.ayugram.proprietary;

import android.util.Pair;
import android.util.SparseArray;
import android.text.TextUtils;

import com.radolyn.ayugram.database.entities.DeletedMessageFull;
import com.radolyn.ayugram.database.entities.DeletedMessageReaction;
import com.radolyn.ayugram.messages.AyuMessagesController;

import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class AyuHistoryHook {
    public static Pair<Integer, Integer> getMinAndMaxIds(ArrayList<MessageObject> messArr) {
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        if (messArr != null) {
            for (int i = 0; i < messArr.size(); i++) {
                MessageObject obj = messArr.get(i);
                if (obj == null) {
                    continue;
                }
                int id = obj.getId();
                if (id < min) {
                    min = id;
                }
                if (id > max) {
                    max = id;
                }
            }
        }
        if (min == Integer.MAX_VALUE) {
            min = 0;
        }
        if (max == Integer.MIN_VALUE) {
            max = 0;
        }
        return new Pair<>(min, max);
    }

    public static void doHook(int currentAccount, ArrayList<MessageObject> messArr, SparseArray<MessageObject>[] messagesDict, int loadIndex, int startId, int endId, long dialogId, int limit, int topicId, boolean isSecretChat) {
        if (messArr == null || messagesDict == null || loadIndex < 0 || loadIndex >= messagesDict.length) {
            return;
        }
        if (startId > endId) {
            int t = startId;
            startId = endId;
            endId = t;
        }
        if (limit <= 0) {
            limit = 500;
        }
        long userId;
        List<DeletedMessageFull> saved;
        try {
            userId = UserConfig.getInstance(currentAccount).getClientUserId();
            saved = AyuMessagesController.getInstance().getMessages(userId, dialogId, topicId, startId, endId, limit);
        } catch (Exception e) {
            FileLog.e("AyuHistoryHook.doHook", e);
            return;
        }
        if (saved == null || saved.isEmpty()) {
            return;
        }
        boolean ascending = isAscending(messArr);
        HashSet<Integer> present = new HashSet<>();
        for (int i = 0; i < messArr.size(); i++) {
            MessageObject obj = messArr.get(i);
            if (obj != null) {
                present.add(obj.getId());
            }
        }
        for (int i = 0; i < saved.size(); i++) {
            DeletedMessageFull full = saved.get(i);
            if (full == null || full.message == null) {
                continue;
            }
            if (messagesDict[loadIndex].indexOfKey(full.message.messageId) >= 0 || present.contains(full.message.messageId)) {
                continue;
            }
            MessageObject obj = buildMessage(currentAccount, full);
            if (obj == null) {
                continue;
            }
            insertSorted(messArr, obj, ascending);
            messagesDict[loadIndex].put(obj.getId(), obj);
            present.add(obj.getId());
            if (full.message.groupedId != 0) {
                addGrouped(currentAccount, messArr, messagesDict[loadIndex], full.message.groupedId, full.message.dialogId, userId, present, ascending);
            }
        }
    }

    private static void addGrouped(int currentAccount, ArrayList<MessageObject> messArr, SparseArray<MessageObject> dict, long groupedId, long dialogId, long userId, HashSet<Integer> present, boolean ascending) {
        List<DeletedMessageFull> siblings;
        try {
            siblings = AyuMessagesController.getInstance().getMessagesGrouped(userId, dialogId, groupedId);
        } catch (Exception e) {
            FileLog.e("AyuHistoryHook.addGrouped", e);
            return;
        }
        if (siblings == null) {
            return;
        }
        for (int i = 0; i < siblings.size(); i++) {
            DeletedMessageFull full = siblings.get(i);
            if (full == null || full.message == null) {
                continue;
            }
            if (dict.indexOfKey(full.message.messageId) >= 0 || present.contains(full.message.messageId)) {
                continue;
            }
            MessageObject obj = buildMessage(currentAccount, full);
            if (obj == null) {
                continue;
            }
            insertSorted(messArr, obj, ascending);
            dict.put(obj.getId(), obj);
            present.add(obj.getId());
        }
    }

    private static MessageObject buildMessage(int currentAccount, DeletedMessageFull full) {
        try {
            TLRPC.TL_message tl = new TLRPC.TL_message();
            AyuMessageUtils.map(full.message, tl, currentAccount);
            AyuMessageUtils.mapMedia(full.message, tl);
            applyReactions(full, tl);
            return new MessageObject(currentAccount, tl, false, true);
        } catch (Exception e) {
            FileLog.e("AyuHistoryHook.buildMessage", e);
            return null;
        }
    }

    private static void applyReactions(DeletedMessageFull full, TLRPC.TL_message tl) {
        if (full.reactions == null || full.reactions.isEmpty()) {
            return;
        }
        try {
            TLRPC.TL_messageReactions reactions = new TLRPC.TL_messageReactions();
            reactions.results = new ArrayList<>();
            for (int i = 0; i < full.reactions.size(); i++) {
                DeletedMessageReaction saved = full.reactions.get(i);
                if (saved == null) {
                    continue;
                }
                TLRPC.Reaction reaction = null;
                if (!TextUtils.isEmpty(saved.emoticon)) {
                    TLRPC.TL_reactionEmoji emoji = new TLRPC.TL_reactionEmoji();
                    emoji.emoticon = saved.emoticon;
                    reaction = emoji;
                } else if (saved.documentId != 0) {
                    TLRPC.TL_reactionCustomEmoji custom = new TLRPC.TL_reactionCustomEmoji();
                    custom.document_id = saved.documentId;
                    reaction = custom;
                }
                if (reaction == null) {
                    continue;
                }
                TLRPC.TL_reactionCount count = new TLRPC.TL_reactionCount();
                count.chosen = saved.selfSelected;
                count.flags = saved.selfSelected ? 1 : 0;
                count.chosen_order = 0;
                count.reaction = reaction;
                count.count = saved.count;
                reactions.results.add(count);
            }
            if (!reactions.results.isEmpty()) {
                tl.reactions = reactions;
            }
        } catch (Exception e) {
            FileLog.e("AyuHistoryHook.applyReactions", e);
        }
    }

    private static boolean isAscending(ArrayList<MessageObject> messArr) {
        Integer first = null;
        Integer second = null;
        for (int i = 0; i < messArr.size(); i++) {
            MessageObject obj = messArr.get(i);
            if (obj == null) {
                continue;
            }
            if (first == null) {
                first = obj.getId();
            } else {
                second = obj.getId();
                break;
            }
        }
        if (first == null || second == null) {
            return true;
        }
        return second > first;
    }

    private static void insertSorted(ArrayList<MessageObject> messArr, MessageObject obj, boolean ascending) {
        int id = obj.getId();
        for (int i = 0; i < messArr.size(); i++) {
            MessageObject cur = messArr.get(i);
            if (cur == null) {
                continue;
            }
            int curId = cur.getId();
            if (ascending ? id < curId : id > curId) {
                messArr.add(i, obj);
                return;
            }
        }
        messArr.add(obj);
    }
}
