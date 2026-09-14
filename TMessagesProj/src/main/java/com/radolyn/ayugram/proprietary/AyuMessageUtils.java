package com.radolyn.ayugram.proprietary;

import android.text.TextUtils;

import com.radolyn.ayugram.AyuConstants;
import com.radolyn.ayugram.database.entities.AyuMessageBase;
import com.radolyn.ayugram.database.entities.DeletedMessage;
import com.radolyn.ayugram.database.entities.EditedMessage;
import com.radolyn.ayugram.messages.AyuMessagesController;
import com.radolyn.ayugram.messages.AyuSavePreferences;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.tgnet.SerializedData;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;

import java.io.File;
import java.util.ArrayList;

public class AyuMessageUtils {
    private static final int VECTOR_MAGIC = 0x1cb5c415;

    //region save: TLRPC.Message -> entity

    public static void map(AyuSavePreferences prefs, EditedMessage out) {
        if (prefs == null || out == null) {
            return;
        }
        mapBase(prefs, prefs.getMessage(), out);
    }

    public static void map(AyuSavePreferences prefs, DeletedMessage out) {
        if (prefs == null || out == null) {
            return;
        }
        mapBase(prefs, prefs.getMessage(), out);
    }

    private static void mapBase(AyuSavePreferences prefs, TLRPC.Message msg, AyuMessageBase out) {
        out.userId = prefs.getUserId();
        out.dialogId = prefs.getDialogId();
        out.topicId = prefs.getTopicId();
        out.messageId = prefs.getMessageId();
        if (msg == null) {
            return;
        }
        out.date = msg.date;
        out.flags = msg.flags;
        out.editDate = msg.edit_date;
        out.views = msg.views;
        out.peerId = msg.peer_id != null ? DialogObject.getPeerDialogId(msg.peer_id) : prefs.getDialogId();
        out.fromId = msg.from_id != null ? DialogObject.getPeerDialogId(msg.from_id) : 0;
        out.groupedId = msg.grouped_id;
        if (msg.fwd_from != null) {
            TLRPC.MessageFwdHeader fwd = msg.fwd_from;
            out.fwdFlags = fwd.flags;
            out.fwdFromId = fwd.from_id != null ? DialogObject.getPeerDialogId(fwd.from_id) : 0;
            out.fwdName = fwd.from_name;
            out.fwdDate = fwd.date;
            out.fwdPostAuthor = fwd.post_author;
        }
        if (msg.reply_to != null) {
            TLRPC.TL_messageReplyHeader reply = msg.reply_to;
            out.replyFlags = reply.flags;
            out.replyMessageId = reply.reply_to_msg_id;
            out.replyPeerId = reply.reply_to_peer_id != null ? DialogObject.getPeerDialogId(reply.reply_to_peer_id) : 0;
            out.replyTopId = reply.reply_to_top_id;
            out.replyForumTopic = reply.forum_topic;
        }
        out.text = msg.message;
        out.textEntities = serializeEntities(msg.entities);
        if (out.entityCreateDate == 0) {
            out.entityCreateDate = (int) (System.currentTimeMillis() / 1000);
        }
    }

    public static void mapMedia(AyuSavePreferences prefs, EditedMessage out, boolean saveFile) {
        if (prefs == null || out == null) {
            return;
        }
        mapMedia(prefs.getMessage(), prefs.getAccountId(), out, saveFile);
    }

    public static void mapMedia(AyuSavePreferences prefs, DeletedMessage out, boolean saveFile) {
        if (prefs == null || out == null) {
            return;
        }
        mapMedia(prefs.getMessage(), prefs.getAccountId(), out, saveFile);
    }

    private static void mapMedia(TLRPC.Message msg, int account, AyuMessageBase out, boolean saveFile) {
        out.documentType = AyuConstants.DOCUMENT_TYPE_NONE;
        out.mimeType = null;
        out.mediaPath = null;
        out.hqThumbPath = null;
        out.documentSerialized = null;
        out.thumbsSerialized = null;
        out.documentAttributesSerialized = null;
        if (msg == null || msg.media == null) {
            return;
        }
        TLRPC.PhotoSize thumb = null;
        if (msg.media instanceof TLRPC.TL_messageMediaPhoto) {
            TLRPC.TL_messageMediaPhoto photoMedia = (TLRPC.TL_messageMediaPhoto) msg.media;
            if (photoMedia.photo == null || photoMedia.photo instanceof TLRPC.TL_photoEmpty) {
                return;
            }
            out.documentType = AyuConstants.DOCUMENT_TYPE_PHOTO;
            out.mimeType = "image/jpeg";
            out.documentSerialized = serializeTl(photoMedia.photo);
            out.thumbsSerialized = serializeSizes(photoMedia.photo.sizes);
            thumb = FileLoader.getClosestPhotoSizeWithSize(photoMedia.photo.sizes, 1280);
        } else if (msg.media instanceof TLRPC.TL_messageMediaDocument) {
            TLRPC.TL_messageMediaDocument docMedia = (TLRPC.TL_messageMediaDocument) msg.media;
            if (docMedia.document == null || docMedia.document instanceof TLRPC.TL_documentEmpty) {
                return;
            }
            TLRPC.Document doc = docMedia.document;
            boolean sticker = MessageObject.isStickerDocument(doc);
            out.documentType = sticker ? AyuConstants.DOCUMENT_TYPE_STICKER : AyuConstants.DOCUMENT_TYPE_FILE;
            out.mimeType = doc.mime_type;
            out.documentSerialized = serializeTl(doc);
            out.thumbsSerialized = serializeSizes(doc.thumbs);
            out.documentAttributesSerialized = serializeAttrs(doc.attributes);
            thumb = FileLoader.getClosestPhotoSizeWithSize(doc.thumbs, 1280);
        } else {
            return;
        }
        if (!saveFile) {
            return;
        }
        try {
            File src = FileLoader.getInstance(account).getPathToMessage(msg);
            if (src != null && src.exists() && src.length() > 0) {
                File dest = uniqueFile(msg, false);
                if (dest != null && AndroidUtilities.copyFile(src, dest)) {
                    out.mediaPath = dest.getAbsolutePath();
                }
            }
            if (thumb != null && thumb.location != null) {
                File thumbSrc = FileLoader.getInstance(account).getPathToAttach(thumb, true);
                if (thumbSrc != null && thumbSrc.exists() && thumbSrc.length() > 0) {
                    File thumbDest = uniqueFile(msg, true);
                    if (thumbDest != null && AndroidUtilities.copyFile(thumbSrc, thumbDest)) {
                        out.hqThumbPath = thumbDest.getAbsolutePath();
                    }
                }
            }
        } catch (Exception e) {
            FileLog.e("AyuMessageUtils.mapMedia", e);
        }
    }

    private static File uniqueFile(TLRPC.Message msg, boolean thumb) {
        try {
            File dir = AyuMessagesController.attachmentsPath;
            if (!dir.exists() && !dir.mkdirs()) {
                return null;
            }
            String ext = thumb ? "jpg" : "dat";
            if (!thumb && msg != null && msg.media instanceof TLRPC.TL_messageMediaDocument) {
                TLRPC.Document doc = ((TLRPC.TL_messageMediaDocument) msg.media).document;
                if (doc != null && !TextUtils.isEmpty(doc.mime_type)) {
                    int slash = doc.mime_type.indexOf('/');
                    if (slash >= 0 && slash + 1 < doc.mime_type.length()) {
                        String sub = doc.mime_type.substring(slash + 1).replaceAll("[^a-zA-Z0-9]", "");
                        if (!TextUtils.isEmpty(sub)) {
                            ext = sub;
                        }
                    }
                }
            }
            long dialogId = msg != null && msg.peer_id != null ? DialogObject.getPeerDialogId(msg.peer_id) : 0;
            String name = dialogId + "_" + (msg != null ? msg.id : 0) + "_" + System.currentTimeMillis() + (thumb ? "_thumb" : "") + "." + ext;
            return new File(dir, name);
        } catch (Exception e) {
            FileLog.e("AyuMessageUtils.uniqueFile", e);
            return null;
        }
    }

    //endregion

    //region load: entity -> TLRPC.TL_message

    public static void map(EditedMessage in, TLRPC.TL_message out, int currentAccount) {
        if (in == null || out == null) {
            return;
        }
        mapBase(in, out, currentAccount, false);
    }

    public static void map(DeletedMessage in, TLRPC.TL_message out, int currentAccount) {
        if (in == null || out == null) {
            return;
        }
        mapBase(in, out, currentAccount, true);
    }

    private static void mapBase(AyuMessageBase in, TLRPC.TL_message out, int currentAccount, boolean deleted) {
        out.id = in.messageId;
        out.date = in.date != 0 ? in.date : in.entityCreateDate;
        out.flags = in.flags;
        out.message = in.text != null ? in.text : "";
        out.entities = deserializeEntities(in.textEntities);
        out.peer_id = peerFromId(in.peerId, currentAccount);
        out.from_id = in.fromId != 0 ? peerFromId(in.fromId, currentAccount) : null;
        out.grouped_id = in.groupedId;
        out.edit_date = in.editDate;
        out.views = in.views;
        out.dialog_id = in.dialogId;
        out.ayuDeleted = deleted;
        if (in.fwdDate != 0 || !TextUtils.isEmpty(in.fwdName)) {
            TLRPC.TL_messageFwdHeader fwd = new TLRPC.TL_messageFwdHeader();
            fwd.flags = in.fwdFlags;
            fwd.from_id = in.fwdFromId != 0 ? peerFromId(in.fwdFromId, currentAccount) : null;
            fwd.from_name = in.fwdName;
            fwd.date = in.fwdDate;
            fwd.post_author = in.fwdPostAuthor;
            out.fwd_from = fwd;
        }
        if (in.replyMessageId != 0) {
            TLRPC.TL_messageReplyHeader reply = new TLRPC.TL_messageReplyHeader();
            reply.flags = in.replyFlags;
            reply.reply_to_msg_id = in.replyMessageId;
            reply.reply_to_peer_id = in.replyPeerId != 0 ? peerFromId(in.replyPeerId, currentAccount) : null;
            reply.reply_to_top_id = in.replyTopId;
            reply.forum_topic = in.replyForumTopic;
            out.reply_to = reply;
        }
        if (!TextUtils.isEmpty(in.mediaPath)) {
            File f = new File(in.mediaPath);
            if (f.exists()) {
                out.attachPath = in.mediaPath;
            }
        }
    }

    public static void mapMedia(EditedMessage in, TLRPC.TL_message out) {
        if (in == null || out == null) {
            return;
        }
        mapMedia(in, out, false);
    }

    public static void mapMedia(DeletedMessage in, TLRPC.TL_message out) {
        if (in == null || out == null) {
            return;
        }
        mapMedia(in, out, true);
    }

    private static void mapMedia(AyuMessageBase in, TLRPC.TL_message out, boolean deleted) {
        if (in.documentType == AyuConstants.DOCUMENT_TYPE_NONE || in.documentSerialized == null) {
            return;
        }
        try {
            if (in.documentType == AyuConstants.DOCUMENT_TYPE_PHOTO) {
                TLRPC.Photo photo = deserializePhoto(in.documentSerialized);
                if (photo != null && !(photo instanceof TLRPC.TL_photoEmpty)) {
                    TLRPC.TL_messageMediaPhoto media = new TLRPC.TL_messageMediaPhoto();
                    media.photo = photo;
                    out.media = media;
                }
            } else {
                TLRPC.Document doc = deserializeDocument(in.documentSerialized);
                if (doc != null && !(doc instanceof TLRPC.TL_documentEmpty)) {
                    TLRPC.TL_messageMediaDocument media = new TLRPC.TL_messageMediaDocument();
                    media.document = doc;
                    out.media = media;
                }
            }
        } catch (Exception e) {
            FileLog.e("AyuMessageUtils.mapMedia", e);
        }
        if (TextUtils.isEmpty(out.attachPath) && !TextUtils.isEmpty(in.hqThumbPath)) {
            File f = new File(in.hqThumbPath);
            if (f.exists() && in.documentType == AyuConstants.DOCUMENT_TYPE_PHOTO) {
                out.attachPath = in.hqThumbPath;
            }
        }
    }

    //endregion

    //region peers

    public static TLRPC.Peer peerFromId(long id, int currentAccount) {
        if (id == 0) {
            return null;
        }
        if (id > 0) {
            TLRPC.TL_peerUser peer = new TLRPC.TL_peerUser();
            peer.user_id = id;
            return peer;
        }
        try {
            TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-id);
            if (chat != null && ChatObject.isChannel(chat)) {
                TLRPC.TL_peerChannel peer = new TLRPC.TL_peerChannel();
                peer.channel_id = -id;
                return peer;
            }
        } catch (Exception e) {
            FileLog.e("AyuMessageUtils.peerFromId", e);
        }
        TLRPC.TL_peerChat peer = new TLRPC.TL_peerChat();
        peer.chat_id = -id;
        return peer;
    }

    //endregion

    //region tl serialization

    private static byte[] serializeEntities(ArrayList<TLRPC.MessageEntity> entities) {
        if (entities == null || entities.isEmpty()) {
            return null;
        }
        SerializedData data = null;
        try {
            data = new SerializedData(entities.size() * 32 + 8);
            data.writeInt32(VECTOR_MAGIC);
            data.writeInt32(entities.size());
            for (int i = 0; i < entities.size(); i++) {
                entities.get(i).serializeToStream(data);
            }
            return data.toByteArray();
        } catch (Exception e) {
            FileLog.e("AyuMessageUtils.serializeEntities", e);
            return null;
        } finally {
            if (data != null) {
                data.cleanup();
            }
        }
    }

    private static ArrayList<TLRPC.MessageEntity> deserializeEntities(byte[] bytes) {
        ArrayList<TLRPC.MessageEntity> result = new ArrayList<>();
        if (bytes == null || bytes.length < 8) {
            return result;
        }
        SerializedData data = null;
        try {
            data = new SerializedData(bytes);
            if (data.readInt32(false) != VECTOR_MAGIC) {
                return result;
            }
            int count = data.readInt32(false);
            for (int i = 0; i < count; i++) {
                TLRPC.MessageEntity entity = TLRPC.MessageEntity.TLdeserialize(data, data.readInt32(false), false);
                if (entity == null) {
                    break;
                }
                result.add(entity);
            }
        } catch (Exception e) {
            FileLog.e("AyuMessageUtils.deserializeEntities", e);
        } finally {
            if (data != null) {
                data.cleanup();
            }
        }
        return result;
    }

    private static byte[] serializeTl(TLObject object) {
        if (object == null) {
            return null;
        }
        SerializedData data = null;
        try {
            data = new SerializedData(4096);
            object.serializeToStream(data);
            return data.toByteArray();
        } catch (Exception e) {
            FileLog.e("AyuMessageUtils.serializeTl", e);
            return null;
        } finally {
            if (data != null) {
                data.cleanup();
            }
        }
    }

    private static byte[] serializeSizes(ArrayList<TLRPC.PhotoSize> sizes) {
        if (sizes == null || sizes.isEmpty()) {
            return null;
        }
        SerializedData data = null;
        try {
            data = new SerializedData(sizes.size() * 256 + 8);
            data.writeInt32(VECTOR_MAGIC);
            data.writeInt32(sizes.size());
            for (int i = 0; i < sizes.size(); i++) {
                sizes.get(i).serializeToStream(data);
            }
            return data.toByteArray();
        } catch (Exception e) {
            FileLog.e("AyuMessageUtils.serializeSizes", e);
            return null;
        } finally {
            if (data != null) {
                data.cleanup();
            }
        }
    }

    private static byte[] serializeAttrs(ArrayList<TLRPC.DocumentAttribute> attrs) {
        if (attrs == null || attrs.isEmpty()) {
            return null;
        }
        SerializedData data = null;
        try {
            data = new SerializedData(attrs.size() * 256 + 8);
            data.writeInt32(VECTOR_MAGIC);
            data.writeInt32(attrs.size());
            for (int i = 0; i < attrs.size(); i++) {
                attrs.get(i).serializeToStream(data);
            }
            return data.toByteArray();
        } catch (Exception e) {
            FileLog.e("AyuMessageUtils.serializeAttrs", e);
            return null;
        } finally {
            if (data != null) {
                data.cleanup();
            }
        }
    }

    private static TLRPC.Photo deserializePhoto(byte[] bytes) {
        if (bytes == null || bytes.length < 4) {
            return null;
        }
        SerializedData data = null;
        try {
            data = new SerializedData(bytes);
            return TLRPC.Photo.TLdeserialize(data, data.readInt32(false), false);
        } catch (Exception e) {
            FileLog.e("AyuMessageUtils.deserializePhoto", e);
            return null;
        } finally {
            if (data != null) {
                data.cleanup();
            }
        }
    }

    private static TLRPC.Document deserializeDocument(byte[] bytes) {
        if (bytes == null || bytes.length < 4) {
            return null;
        }
        SerializedData data = null;
        try {
            data = new SerializedData(bytes);
            return TLRPC.Document.TLdeserialize(data, data.readInt32(false), false);
        } catch (Exception e) {
            FileLog.e("AyuMessageUtils.deserializeDocument", e);
            return null;
        } finally {
            if (data != null) {
                data.cleanup();
            }
        }
    }

    //endregion
}
