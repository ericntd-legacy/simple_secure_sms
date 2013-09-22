package sg.edu.dukenus.simplesecuresms;

import java.util.Set;

import org.thoughtcrime.securesms.database.model.ThreadRecord;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.Recipients;
import org.thoughtcrime.securesms.util.Emoji;

import android.content.Context;
import android.content.Intent;
import android.content.res.TypedArray;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.provider.Contacts.Intents;
import android.provider.ContactsContract.QuickContact;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.format.DateUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;
import android.widget.QuickContactBadge;
import android.widget.RelativeLayout;
import android.widget.TextView;

public class ConversationListItemSSS extends RelativeLayout implements
		Recipient.RecipientModifiedListener {

	private Context context;
	private Set<Long> selectedThreads;
	private Recipients recipients;
	private long threadId;
	private TextView subjectView;
	private TextView fromView;
	private TextView dateView;
	private long count;
	private boolean read;

	private ImageView contactPhotoImage;
	private QuickContactBadge contactPhotoBadge;

	private final Handler handler = new Handler();
	private int distributionType;

	public ConversationListItemSSS(Context context) {
		super(context);
		this.context = context;
	}

	public ConversationListItemSSS(Context context, AttributeSet attrs) {
		super(context, attrs);
		this.context = context;
	}

	@Override
	protected void onFinishInflate() {
		this.subjectView = (TextView) findViewById(R.id.subject);
		this.fromView = (TextView) findViewById(R.id.from);
		this.dateView = (TextView) findViewById(R.id.date);

		this.contactPhotoBadge = (QuickContactBadge) findViewById(R.id.contact_photo_badge);
		this.contactPhotoImage = (ImageView) findViewById(R.id.contact_photo_image);

		initializeContactWidgetVisibility();
	}

	public void set(ThreadRecord thread, Set<Long> selectedThreads,
			boolean batchMode) {
		this.selectedThreads = selectedThreads;
		this.recipients = thread.getRecipients();
		this.threadId = thread.getThreadId();
		this.count = thread.getCount();
		this.read = thread.isRead();
		this.distributionType = thread.getDistributionType();

		this.recipients.addListener(this);
		this.fromView.setText(formatFrom(recipients, count, read));
		this.subjectView.setText(
				Emoji.getInstance(context).emojify(thread.getDisplayBody(),
						Emoji.EMOJI_SMALL), TextView.BufferType.SPANNABLE);

		if (thread.getDate() > 0)
			this.dateView.setText(DateUtils.getRelativeTimeSpanString(
					getContext(), thread.getDate(), false));

		setBackground(read, batchMode);
		setContactPhoto(this.recipients.getPrimaryRecipient());
	}

	public void unbind() {
		if (this.recipients != null)
			this.recipients.removeListener(this);
	}

	private void initializeContactWidgetVisibility() {
		if (isBadgeEnabled()) {
			contactPhotoBadge.setVisibility(View.VISIBLE);
			contactPhotoImage.setVisibility(View.GONE);
		} else {
			contactPhotoBadge.setVisibility(View.GONE);
			contactPhotoImage.setVisibility(View.VISIBLE);
		}
	}

	private void setContactPhoto(final Recipient recipient) {
		if (recipient == null)
			return;

		if (isBadgeEnabled()) {
			contactPhotoBadge.setImageBitmap(recipient.getContactPhoto());
			contactPhotoBadge.assignContactFromPhone(recipient.getNumber(),
					true);
		} else {
			contactPhotoImage.setImageBitmap(recipient.getContactPhoto());
			contactPhotoImage.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					if (recipient.getContactUri() != null) {
						QuickContact.showQuickContact(context,
								contactPhotoImage, recipient.getContactUri(),
								QuickContact.MODE_LARGE, null);
					} else {
						Intent intent = new Intent(
								Intents.SHOW_OR_CREATE_CONTACT, Uri.fromParts(
										"tel", recipient.getNumber(), null));
						context.startActivity(intent);
					}
				}
			});
		}
	}

	private void setBackground(boolean read, boolean batch) {
		int[] attributes = new int[] {
				R.attr.conversation_list_item_background_selected,
				R.attr.conversation_list_item_background_read,
				R.attr.conversation_list_item_background_unread };

		TypedArray drawables = context.obtainStyledAttributes(attributes);

		if (batch && selectedThreads.contains(threadId)) {
			setBackgroundDrawable(drawables.getDrawable(0));
		} else if (read) {
			setBackgroundDrawable(drawables.getDrawable(1));
		} else {
			setBackgroundDrawable(drawables.getDrawable(2));
		}

		drawables.recycle();
	}

	private boolean isBadgeEnabled() {
		return Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB;
	}

	private CharSequence formatFrom(Recipients from, long count, boolean read) {
		int attributes[] = new int[] { R.attr.conversation_list_item_count_color };
		TypedArray colors = context.obtainStyledAttributes(attributes);

		String fromString = from.toShortString();
		SpannableStringBuilder builder = new SpannableStringBuilder(fromString);

		if (count > 0) {
			builder.append(" " + count);
			builder.setSpan(new ForegroundColorSpan(colors.getColor(0, 0)),
					fromString.length(), builder.length(),
					Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
		}

		if (!read) {
			builder.setSpan(new StyleSpan(Typeface.BOLD), 0, builder.length(),
					Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
		}

		colors.recycle();
		return builder;
	}

	public Recipients getRecipients() {
		return recipients;
	}

	public long getThreadId() {
		return threadId;
	}

	public int getDistributionType() {
		return distributionType;
	}

	@Override
	public void onModified(Recipient recipient) {
		handler.post(new Runnable() {
			@Override
			public void run() {
				ConversationListItemSSS.this.fromView.setText(formatFrom(
						recipients, count, read));
				setContactPhoto(ConversationListItemSSS.this.recipients
						.getPrimaryRecipient());
			}
		});
	}
}
