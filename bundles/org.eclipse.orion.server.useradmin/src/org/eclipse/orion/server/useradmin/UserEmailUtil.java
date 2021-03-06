/*******************************************************************************
 * Copyright (c) 2012 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.useradmin;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Properties;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.orion.server.core.PreferenceHelper;
import org.eclipse.orion.server.core.ServerConstants;

/**
 * Handles sending emails to users
 *
 */
public class UserEmailUtil {

	private static UserEmailUtil util = null;
	private static final String EMAIL_CONFIRMATION_FILE = "/emails/EmailConfirmation.txt"; //$NON-NLS-1$
	private static final String EMAIL_CONFIRMATION_RESET_PASS_FILE = "/emails/EmailConfirmationPasswordReset.txt"; //$NON-NLS-1$
	private static final String EMAIL_PASSWORD_RESET = "/emails/PasswordReset.txt"; //$NON-NLS-1$
	private static final String EMAIL_URL_LINK = "<URL>"; //$NON-NLS-1$
	private static final String EMAIL_USER_LINK = "<USER>"; //$NON-NLS-1$
	private static final String EMAIL_PASSWORD_LINK = "<PASSWORD>"; //$NON-NLS-1$
	private Properties properties;
	private EmailContent confirmationEmail;
	private EmailContent confirmationResetPassEmail;
	private EmailContent passwordResetEmail;

	private class EmailContent {
		private String title;
		private String content;

		public String getTitle() {
			return title;
		}

		public String getContent() {
			return content;
		}

		public EmailContent(String fileName) throws URISyntaxException, IOException {
			URL entry = UserAdminActivator.getDefault().getBundleContext().getBundle().getEntry(fileName);
			if (entry == null)
				throw new IOException("File not found: " + fileName);
			BufferedReader reader = new BufferedReader(new InputStreamReader(entry.openStream()));
			String line = null;
			try {
				title = reader.readLine();
				StringBuilder stringBuilder = new StringBuilder();
				String ls = System.getProperty("line.separator");
				while ((line = reader.readLine()) != null) {
					stringBuilder.append(line);
					stringBuilder.append(ls);
				}
				content = stringBuilder.toString();
			} finally {
				reader.close();
			}
		}
	};

	public UserEmailUtil() {
		properties = System.getProperties();
		properties.put("mail.smtp.starttls.enable", PreferenceHelper.getString(ServerConstants.CONFIG_MAIL_SMTP_STARTTLS, "true"));

		if (PreferenceHelper.getString(ServerConstants.CONFIG_MAIL_SMTP_HOST, null) != null)
			properties.put("mail.smtp.host", PreferenceHelper.getString(ServerConstants.CONFIG_MAIL_SMTP_HOST, null));

		if (PreferenceHelper.getString("mail.smtp.port", null) != null)
			properties.put("mail.smtp.port", PreferenceHelper.getString("mail.smtp.port", null));

		if (PreferenceHelper.getString("mail.smtp.user", null) != null)
			properties.put("mail.smtp.user", PreferenceHelper.getString("mail.smtp.user", null));

		if (PreferenceHelper.getString("mail.smtp.password", null) != null)
			properties.put("mail.smtp.password", PreferenceHelper.getString("mail.smtp.password", null));

		properties.put("mail.smtp.auth", PreferenceHelper.getString("mail.smtp.auth", "false"));
	}

	public static UserEmailUtil getUtil() {
		if (util == null) {
			util = new UserEmailUtil();
		}
		return util;
	}

	public boolean isEmailConfigured() {
		return PreferenceHelper.getString("mail.from", null) != null;
	}

	public void sendEmail(String subject, String messageText, String emailAddress) throws URISyntaxException, IOException, CoreException {
		Session session = Session.getInstance(properties, null);
		InternetAddress from;
		try {
			from = new InternetAddress(PreferenceHelper.getString("mail.from", "OrionAdmin"));

			InternetAddress to = new InternetAddress(emailAddress);

			MimeMessage message = new MimeMessage(session);
			message.setFrom(from);
			message.addRecipient(Message.RecipientType.TO, to);

			message.setSubject(subject);
			message.setText(messageText);

			Transport transport = session.getTransport("smtp");
			transport.connect(properties.getProperty("mail.smtp.host", null), properties.getProperty("mail.smtp.user", null), properties.getProperty("mail.smtp.password", null));
			transport.sendMessage(message, message.getAllRecipients());
			transport.close();
		} catch (AddressException e) {
			throw new CoreException(new Status(IStatus.ERROR, UserAdminActivator.PI_USERADMIN, "", e));
		} catch (MessagingException e) {
			throw new CoreException(new Status(IStatus.ERROR, UserAdminActivator.PI_USERADMIN, "", e));
		}
	}

	public void sendEmailConfirmation(URI baseURI, User user) throws URISyntaxException, IOException, CoreException {
		if (confirmationEmail == null) {
			confirmationEmail = new EmailContent(EMAIL_CONFIRMATION_FILE);
		}
		String confirmURL = baseURI.toURL().toString();
		confirmURL += "/" + user.getUid();
		confirmURL += "?" + UserConstants.KEY_CONFIRMATION_ID + "=" + user.getConfirmationId();
		sendEmail(confirmationEmail.getTitle(), confirmationEmail.getContent().replaceAll(EMAIL_USER_LINK, user.getLogin()).replaceAll(EMAIL_URL_LINK, confirmURL), user.getEmail());
	}

	public void sendResetPasswordConfirmation(URI baseURI, User user) throws URISyntaxException, IOException, CoreException {
		if (confirmationResetPassEmail == null) {
			confirmationResetPassEmail = new EmailContent(EMAIL_CONFIRMATION_RESET_PASS_FILE);
		}
		String confirmURL = baseURI.toURL().toString();
		confirmURL += "/" + user.getUid();
		confirmURL += "?" + UserConstants.KEY_PASSWORD_RESET_CONFIRMATION_ID + "=" + user.getProperty(UserConstants.KEY_PASSWORD_RESET_CONFIRMATION_ID);
		sendEmail(confirmationResetPassEmail.getTitle(), confirmationResetPassEmail.getContent().replaceAll(EMAIL_URL_LINK, confirmURL).replaceAll(EMAIL_USER_LINK, user.getLogin()), user.getEmail());
	}

	public void setPasswordResetEmail(User user) throws URISyntaxException, IOException, CoreException {
		if (passwordResetEmail == null) {
			passwordResetEmail = new EmailContent(EMAIL_PASSWORD_RESET);
		}
		sendEmail(passwordResetEmail.getTitle(), passwordResetEmail.getContent().replaceAll(EMAIL_USER_LINK, user.getLogin()).replaceAll(EMAIL_PASSWORD_LINK, user.getPassword()), user.getEmail());
	}
}
