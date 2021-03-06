package eu.siacs.conversations.ui;

import java.util.Iterator;

import org.openintents.openpgp.util.OpenPgpUtils;

import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentSender.SendIntentException;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract.CommonDataKinds;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Intents;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.QuickContactBadge;
import android.widget.TextView;
import android.widget.Toast;
import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.PgpEngine;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Presences;
import eu.siacs.conversations.utils.UIHelper;

public class ContactDetailsActivity extends XmppActivity {
	public static final String ACTION_VIEW_CONTACT = "view_contact";

	protected ContactDetailsActivity activity = this;

	private Contact contact;
	
	private String accountJid;
	private String contactJid;
	
	private EditText name;
	private TextView contactJidTv;
	private TextView accountJidTv;
	private TextView status;
	private TextView askAgain;
	private CheckBox send;
	private CheckBox receive;
	private QuickContactBadge badge;

	private DialogInterface.OnClickListener removeFromRoster = new DialogInterface.OnClickListener() {

		@Override
		public void onClick(DialogInterface dialog, int which) {
			activity.xmppConnectionService.deleteContactOnServer(contact);
			activity.finish();
		}
	};

	private DialogInterface.OnClickListener editContactNameListener = new DialogInterface.OnClickListener() {

		@Override
		public void onClick(DialogInterface dialog, int which) {
			contact.setServerName(name.getText().toString());
			activity.xmppConnectionService.pushContactToServer(contact);
			populateView();
		}
	};

	private DialogInterface.OnClickListener addToPhonebook = new DialogInterface.OnClickListener() {

		@Override
		public void onClick(DialogInterface dialog, int which) {
			Intent intent = new Intent(Intent.ACTION_INSERT_OR_EDIT);
			intent.setType(Contacts.CONTENT_ITEM_TYPE);
			intent.putExtra(Intents.Insert.IM_HANDLE, contact.getJid());
			intent.putExtra(Intents.Insert.IM_PROTOCOL,
					CommonDataKinds.Im.PROTOCOL_JABBER);
			intent.putExtra("finishActivityOnSaveCompleted", true);
			activity.startActivityForResult(intent, 0);
		}
	};
	private OnClickListener onBadgeClick = new OnClickListener() {

		@Override
		public void onClick(View v) {
			AlertDialog.Builder builder = new AlertDialog.Builder(activity);
			builder.setTitle("Add to phone book");
			builder.setMessage("Do you want to add " + contact.getJid()
					+ " to your phones contact list?");
			builder.setNegativeButton("Cancel", null);
			builder.setPositiveButton("Add", addToPhonebook);
			builder.create().show();
		}
	};

	private LinearLayout keys;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		if (getIntent().getAction().equals(ACTION_VIEW_CONTACT)) {
			this.accountJid = getIntent().getExtras().getString("account");
			this.contactJid = getIntent().getExtras().getString("contact");
		}
		setContentView(R.layout.activity_contact_details);

		contactJidTv = (TextView) findViewById(R.id.details_contactjid);
		accountJidTv = (TextView) findViewById(R.id.details_account);
		status = (TextView) findViewById(R.id.details_contactstatus);
		send = (CheckBox) findViewById(R.id.details_send_presence);
		receive = (CheckBox) findViewById(R.id.details_receive_presence);
		askAgain = (TextView) findViewById(R.id.ask_again);
		badge = (QuickContactBadge) findViewById(R.id.details_contact_badge);
		keys = (LinearLayout) findViewById(R.id.details_contact_keys);
		getActionBar().setHomeButtonEnabled(true);
		getActionBar().setDisplayHomeAsUpEnabled(true);

	}

	@Override
	public boolean onOptionsItemSelected(MenuItem menuItem) {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setNegativeButton("Cancel", null);
		switch (menuItem.getItemId()) {
		case android.R.id.home:
			finish();
			break;
		case R.id.action_delete_contact:
			builder.setTitle("Delete from roster")
					.setMessage(
							getString(R.string.remove_contact_text,
									contact.getJid()))
					.setPositiveButton("Delete", removeFromRoster).create()
					.show();
			break;
		case R.id.action_edit_contact:
			if (contact.getSystemAccount() == null) {

				View view = (View) getLayoutInflater().inflate(
						R.layout.edit_contact_name, null);
				name = (EditText) view.findViewById(R.id.editText1);
				name.setText(contact.getDisplayName());
				builder.setView(view).setTitle(contact.getJid())
						.setPositiveButton("Edit", editContactNameListener)
						.create().show();

			} else {
				Intent intent = new Intent(Intent.ACTION_EDIT);
				String[] systemAccount = contact.getSystemAccount().split("#");
				long id = Long.parseLong(systemAccount[0]);
				Uri uri = Contacts.getLookupUri(id, systemAccount[1]);
				intent.setDataAndType(uri, Contacts.CONTENT_ITEM_TYPE);
				intent.putExtra("finishActivityOnSaveCompleted", true);
				startActivity(intent);
			}
			break;
		}
		return super.onOptionsItemSelected(menuItem);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.contact_details, menu);
		return true;
	}

	private void populateView() {
		setTitle(contact.getDisplayName());
		if (contact.getOption(Contact.Options.FROM)) {
			send.setChecked(true);
		} else {
			send.setText(R.string.preemptively_grant);
			if (contact
					.getOption(Contact.Options.PREEMPTIVE_GRANT)) {
				send.setChecked(true);
			} else {
				send.setChecked(false);
			}
		}
		if (contact.getOption(Contact.Options.TO)) {
			receive.setChecked(true);
		} else {
			receive.setText(R.string.ask_for_presence_updates);
			askAgain.setVisibility(View.VISIBLE);
			askAgain.setOnClickListener(new OnClickListener() {
				
				@Override
				public void onClick(View v) {
					Toast.makeText(getApplicationContext(), "Asked for presence updates",Toast.LENGTH_SHORT).show();
					xmppConnectionService.requestPresenceUpdatesFrom(contact);
					
				}
			});
			if (contact.getOption(Contact.Options.ASKING)) {
				receive.setChecked(true);
			} else {
				receive.setChecked(false);
			}
		}

		switch (contact.getMostAvailableStatus()) {
		case Presences.CHAT:
			status.setText("free to chat");
			status.setTextColor(0xFF83b600);
			break;
		case Presences.ONLINE:
			status.setText("online");
			status.setTextColor(0xFF83b600);
			break;
		case Presences.AWAY:
			status.setText("away");
			status.setTextColor(0xFFffa713);
			break;
		case Presences.XA:
			status.setText("extended away");
			status.setTextColor(0xFFffa713);
			break;
		case Presences.DND:
			status.setText("do not disturb");
			status.setTextColor(0xFFe92727);
			break;
		case Presences.OFFLINE:
			status.setText("offline");
			status.setTextColor(0xFFe92727);
			break;
		default:
			status.setText("offline");
			status.setTextColor(0xFFe92727);
			break;
		}
		if (contact.getPresences().size() > 1) {
			contactJidTv.setText(contact.getJid()+" ("+contact.getPresences().size()+")");
		} else {
			contactJidTv.setText(contact.getJid());
		}
		accountJidTv.setText(contact.getAccount().getJid());

		UIHelper.prepareContactBadge(this, badge, contact, getApplicationContext());

		if (contact.getSystemAccount() == null) {
			badge.setOnClickListener(onBadgeClick);
		}

		keys.removeAllViews();
		LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		for (Iterator<String> iterator = contact.getOtrFingerprints()
				.iterator(); iterator.hasNext();) {
			String otrFingerprint = iterator.next();
			View view = (View) inflater.inflate(R.layout.contact_key, null);
			TextView key = (TextView) view.findViewById(R.id.key);
			TextView keyType = (TextView) view.findViewById(R.id.key_type);
			keyType.setText("OTR Fingerprint");
			key.setText(otrFingerprint);
			keys.addView(view);
		}
		if (contact.getPgpKeyId() != 0) {
			View view = (View) inflater.inflate(R.layout.contact_key, null);
			TextView key = (TextView) view.findViewById(R.id.key);
			TextView keyType = (TextView) view.findViewById(R.id.key_type);
			keyType.setText("PGP Key ID");
			key.setText(OpenPgpUtils.convertKeyIdToHex(contact.getPgpKeyId()));
			view.setOnClickListener(new OnClickListener() {
				
				@Override
				public void onClick(View v) {
					PgpEngine pgp = activity.xmppConnectionService.getPgpEngine();
					if (pgp!=null) {
						PendingIntent intent = pgp.getIntentForKey(contact);
						if (intent!=null) {
							try {
								startIntentSenderForResult(intent.getIntentSender(), 0, null, 0, 0, 0);
							} catch (SendIntentException e) {
								
							}
						}
					}
				}
			});
			keys.addView(view);
		}
	}

	@Override
	public void onBackendConnected() {
		if ((accountJid != null)&&(contactJid != null)) {
			Account account = xmppConnectionService.findAccountByJid(accountJid);
			if (account==null) {
				return;
			}
			this.contact = account.getRoster().getContact(contactJid);
			populateView();
		}
	}

	@Override
	protected void onStop() {
		super.onStop();
		boolean updated = false;
		if (contact.getOption(Contact.Options.FROM)) {
			if (!send.isChecked()) {
				contact.resetOption(Contact.Options.FROM);
				contact.resetOption(Contact.Options.PREEMPTIVE_GRANT);
				activity.xmppConnectionService.stopPresenceUpdatesTo(contact);
				updated = true;
			}
		} else {
			if (contact
					.getOption(Contact.Options.PREEMPTIVE_GRANT)) {
				if (!send.isChecked()) {
					contact.resetOption(Contact.Options.PREEMPTIVE_GRANT);
					updated = true;
				}
			} else {
				if (send.isChecked()) {
					contact.setOption(Contact.Options.PREEMPTIVE_GRANT);
					updated = true;
				}
			}
		}
		if (contact.getOption(Contact.Options.TO)) {
			if (!receive.isChecked()) {
				contact.resetOption(Contact.Options.TO);
				activity.xmppConnectionService.stopPresenceUpdatesFrom(contact);
				updated = true;
			}
		} else {
			if (contact.getOption(Contact.Options.ASKING)) {
				if (!receive.isChecked()) {
					contact.resetOption(Contact.Options.ASKING);
					activity.xmppConnectionService
							.stopPresenceUpdatesFrom(contact);
					updated = true;
				}
			} else {
				if (receive.isChecked()) {
					contact.setOption(Contact.Options.ASKING);
					activity.xmppConnectionService
							.requestPresenceUpdatesFrom(contact);
					updated = true;
				}
			}
		}
		if (updated) {
			Toast.makeText(getApplicationContext(), "Subscription updated", Toast.LENGTH_SHORT).show();
		}
	}

}
