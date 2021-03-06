package ml.docilealligator.infinityforreddit;

import android.os.AsyncTask;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Locale;

import ml.docilealligator.infinityforreddit.API.RedditAPI;
import ml.docilealligator.infinityforreddit.Utils.JSONUtils;
import ml.docilealligator.infinityforreddit.Utils.APIUtils;
import ml.docilealligator.infinityforreddit.Utils.Utils;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;

public class FetchMessages {

    public static final String WHERE_INBOX = "inbox";
    public static final String WHERE_UNREAD = "unread";
    public static final String WHERE_SENT = "sent";
    public static final String WHERE_COMMENTS = "comments";
    public static final String WHERE_MESSAGES = "messages";
    public static final int MESSAGE_TYPE_NOTIFICATION = 0;
    public static final int MESSAGE_TYPE_PRIVATE_MESSAGE = 1;

    static void fetchInbox(Retrofit oauthRetrofit, Locale locale, String accessToken, String where,
                           String after, int messageType, FetchMessagesListener fetchMessagesListener) {
        oauthRetrofit.create(RedditAPI.class).getMessages(APIUtils.getOAuthHeader(accessToken), where, after)
                .enqueue(new Callback<String>() {
                    @Override
                    public void onResponse(@NonNull Call<String> call, @NonNull Response<String> response) {
                        if (response.isSuccessful()) {
                            new ParseMessageAsnycTask(response.body(), locale, messageType,
                                    fetchMessagesListener::fetchSuccess).execute();
                        } else {
                            fetchMessagesListener.fetchFailed();
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<String> call, @NonNull Throwable t) {
                        fetchMessagesListener.fetchFailed();
                    }
                });
    }

    static ArrayList<Message> parseMessage(String response, Locale locale, int messageType) {
        JSONArray messageArray;
        try {
            messageArray = new JSONObject(response).getJSONObject(JSONUtils.DATA_KEY).getJSONArray(JSONUtils.CHILDREN_KEY);
        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }

        ArrayList<Message> messages = new ArrayList<>();
        for (int i = 0; i < messageArray.length(); i++) {
            try {
                JSONObject messageJSON = messageArray.getJSONObject(i);
                String kind = messageJSON.getString(JSONUtils.KIND_KEY);
                if ((messageType == MESSAGE_TYPE_NOTIFICATION && kind.equals("t4")) ||
                        (messageType == MESSAGE_TYPE_PRIVATE_MESSAGE && !kind.equals("t4"))) {
                    continue;
                }

                JSONObject rawMessageJSON = messageJSON.getJSONObject(JSONUtils.DATA_KEY);
                String subredditName = rawMessageJSON.getString(JSONUtils.SUBREDDIT_KEY);
                String subredditNamePrefixed = rawMessageJSON.getString(JSONUtils.SUBREDDIT_NAME_PREFIX_KEY);
                String id = rawMessageJSON.getString(JSONUtils.ID_KEY);
                String fullname = rawMessageJSON.getString(JSONUtils.NAME_KEY);
                String subject = rawMessageJSON.getString(JSONUtils.SUBJECT_KEY);
                String author = rawMessageJSON.getString(JSONUtils.AUTHOR_KEY);
                String parentFullname = rawMessageJSON.getString(JSONUtils.PARENT_ID_KEY);
                String title = rawMessageJSON.has(JSONUtils.LINK_TITLE_KEY) ? rawMessageJSON.getString(JSONUtils.LINK_TITLE_KEY) : null;
                String body = Utils.modifyMarkdown(rawMessageJSON.getString(JSONUtils.BODY_KEY));
                String context = rawMessageJSON.getString(JSONUtils.CONTEXT_KEY);
                String distinguished = rawMessageJSON.getString(JSONUtils.DISTINGUISHED_KEY);
                boolean wasComment = rawMessageJSON.getBoolean(JSONUtils.WAS_COMMENT_KEY);
                boolean isNew = rawMessageJSON.getBoolean(JSONUtils.NEW_KEY);
                int score = rawMessageJSON.getInt(JSONUtils.SCORE_KEY);
                int nComments = rawMessageJSON.isNull(JSONUtils.NUM_COMMENTS_KEY) ? -1 : rawMessageJSON.getInt(JSONUtils.NUM_COMMENTS_KEY);
                long timeUTC = rawMessageJSON.getLong(JSONUtils.CREATED_UTC_KEY) * 1000;

                Calendar submitTimeCalendar = Calendar.getInstance();
                submitTimeCalendar.setTimeInMillis(timeUTC);
                String formattedTime = new SimpleDateFormat("MMM d, yyyy, HH:mm",
                        locale).format(submitTimeCalendar.getTime());

                messages.add(new Message(kind, subredditName, subredditNamePrefixed, id, fullname, subject,
                        author, parentFullname, title, body, context, distinguished, formattedTime,
                        wasComment, isNew, score, nComments, timeUTC));
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return messages;
    }

    interface FetchMessagesListener {
        void fetchSuccess(ArrayList<Message> messages, @Nullable String after);

        void fetchFailed();
    }

    private static class ParseMessageAsnycTask extends AsyncTask<Void, Void, Void> {

        private String response;
        private Locale locale;
        private ArrayList<Message> messages;
        private String after;
        private int messageType;
        private ParseMessageAsyncTaskListener parseMessageAsyncTaskListener;
        ParseMessageAsnycTask(String response, Locale locale, int messageType,
                              ParseMessageAsyncTaskListener parseMessageAsnycTaskListener) {
            this.response = response;
            this.locale = locale;
            this.messageType = messageType;
            messages = new ArrayList<>();
            this.parseMessageAsyncTaskListener = parseMessageAsnycTaskListener;
        }

        @Override
        protected Void doInBackground(Void... voids) {
            messages = parseMessage(response, locale, messageType);
            try {
                after = new JSONObject(response).getJSONObject(JSONUtils.DATA_KEY).getString(JSONUtils.AFTER_KEY);
            } catch (JSONException e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
            parseMessageAsyncTaskListener.parseSuccess(messages, after);
        }

        interface ParseMessageAsyncTaskListener {
            void parseSuccess(ArrayList<Message> messages, @Nullable String after);
        }
    }
}
