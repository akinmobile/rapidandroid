/*
 * Copyright (C) 2009 Dimagi Inc., UNICEF
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 *
 */

/**
 * 
 */
package org.rapidandroid.receiver;

import java.sql.Timestamp;
import java.util.Date;

import org.rapidandroid.content.translation.MessageTranslator;
import org.rapidandroid.data.RapidSmsDBConstants;
import org.rapidsms.java.core.model.Message;
import org.rapidsms.java.core.model.Monitor;

import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.telephony.gsm.SmsMessage;
import android.util.Log;

/**
 * 
 * Initial broadcast receiver for RapidAndroid.
 * 
 * Gets triggered on Android SMS receive event, gets a handle to the message and
 * does the following: - verify that it's what the app wants to process - save
 * message to rapidandroid's db via the content provider - save a new
 * mMonitorString if necessary (that's handled by the content provider save) -
 * delete message from inbox because we don't want it to be in duplicate - upon
 * successful save, trigger a separate event to tell the next process that a
 * save was done.
 * 
 * 
 * 
 * @author Daniel Myung dmyung@dimagi.com
 * @created Jan 12, 2009
 * 
 * 
 * 
 * 
 */
public class SmsReceiver extends BroadcastReceiver {

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.content.BroadcastReceiver#onReceive(android.content.Context,
	 * android.content.Intent)
	 */

	Uri uriSms = Uri.parse("content://sms/inbox");

	private void insertMessageToContentProvider(Context context, SmsMessage mesg) {

		Uri writeMessageUri = RapidSmsDBConstants.Message.CONTENT_URI;

		ContentValues messageValues = new ContentValues();
		messageValues.put(RapidSmsDBConstants.Message.MESSAGE, mesg.getMessageBody());

		Timestamp ts = new Timestamp(mesg.getTimestampMillis());

		Monitor monitor = MessageTranslator.GetMonitorAndInsertIfNew(context, mesg.getOriginatingAddress());

		messageValues.put(RapidSmsDBConstants.Message.MONITOR, monitor.getID());
		messageValues.put(RapidSmsDBConstants.Message.TIME, Message.SQLDateFormatter.format(ts)); // expensive
																									// string
																									// formatting
																									// operation.
		// messageValues.put(RapidSmsDBConstants.Message.TIME,
		// mesg.getTimestampMillis()); //longs don't store as datetimes
		messageValues.put(RapidSmsDBConstants.Message.IS_OUTGOING, false);
		Date now = new Date();
		messageValues.put(RapidSmsDBConstants.Message.RECEIVE_TIME, Message.SQLDateFormatter.format(now)); // profile
																											// has
																											// shown
																											// this
																											// is
																											// an
																											// expensive
																											// operation
		// messageValues.put(RapidSmsDBConstants.Message.RECEIVE_TIME,
		// now.getTime()); //but this doesn't fracking work to convert to a
		// datetime value.
		boolean successfulSave = false;
		Uri msgUri = null;
		try {
			msgUri = context.getContentResolver().insert(writeMessageUri, messageValues);
			successfulSave = true;
		} catch (Exception ex) {

		}

		if (successfulSave) {
			Intent broadcast = new Intent("org.rapidandroid.intents.SMS_SAVED");
			broadcast.putExtra("from", mesg.getOriginatingAddress());
			broadcast.putExtra("body", mesg.getMessageBody());
			broadcast.putExtra("msgid", Integer.valueOf(msgUri.getPathSegments().get(1)));
			//DeleteSMSFromInbox(context, mesg);
			context.sendBroadcast(broadcast);
		}
	}

	private void DeleteSMSFromInbox(Context context, SmsMessage mesg) {
		try {

			StringBuilder sb = new StringBuilder();
			sb.append("address='" + mesg.getOriginatingAddress() + "' AND ");
			sb.append("body='" + mesg.getMessageBody() + "'");
			// sb.append("time='" + mesg.getTimestamp() + "'"); //doesn't seem
			// to be supported
			Cursor c = context.getContentResolver().query(uriSms, null, sb.toString(), null, null);
			c.moveToFirst();
			// String id = c.getString(0);
			int thread_id = c.getInt(1);
			context.getContentResolver().delete(Uri.parse("content://sms/conversations/" + thread_id), null, null);
			c.close();
		} catch (Exception ex) {
			// deletions don't work most of the time since the timing of the
			// receipt and saving to the inbox
			// makes it difficult to match up perfectly. the SMS might not be in
			// the inbox yet when this receiver triggers!
			Log.d("SmsReceiver", "Error deleting sms from inbox: " + ex.getMessage());
		}
	}

	@Override
	// source: http://www.devx.com/wireless/Article/39495/1954
	public void onReceive(Context context, Intent intent) {
		if (!intent.getAction().equals("android.provider.Telephony.SMS_RECEIVED")) {// {
			return;
		}

		SmsMessage msgs[] = getMessagesFromIntent(intent);

		for (int i = 0; i < msgs.length; i++) {
			String message = msgs[i].getDisplayMessageBody();

			if (message != null && message.length() > 0) {
				Log.d("MessageListener", message);

				// //Our trigger message must be generic and human redable
				// because it will end up
				// //In the SMS inbox of the phone.
				// if(message.startsWith("dimagi"))
				// {
				// //DO SOMETHING
				// }

				insertMessageToContentProvider(context, msgs[i]);
			}
		}

	}

	// source: http://www.devx.com/wireless/Article/39495/1954
	private SmsMessage[] getMessagesFromIntent(Intent intent) {
		SmsMessage retMsgs[] = null;
		Bundle bdl = intent.getExtras();
		try {
			Object pdus[] = (Object[]) bdl.get("pdus");
			retMsgs = new SmsMessage[pdus.length];
			for (int n = 0; n < pdus.length; n++) {
				byte[] byteData = (byte[]) pdus[n];
				retMsgs[n] = SmsMessage.createFromPdu(byteData);
			}

		} catch (Exception e) {
			Log.e("GetMessages", "fail", e);
		}
		return retMsgs;
	}

}
