package cl.vc.module.protocolbuff.quickfix;

import lombok.extern.slf4j.Slf4j;
import quickfix.*;
import quickfix.fix42.MessageCracker;


@Slf4j
public abstract class QuickFix42Connection extends MessageCracker implements Application {

	private static final long SLEEP_TIME = 2000L;
	private final String sessionsFile;
	private SocketInitiator socketInitiator = null;
	private SocketAcceptor socketAcceptor = null;

	public QuickFix42Connection(String sessionsFile, boolean hasInitiator, boolean hasAcceptor) {
		this.sessionsFile = sessionsFile;

		try {
			SessionSettings sessionSettings = new SessionSettings(sessionsFile);
			FileStoreFactory fileStoreFactory = new FileStoreFactory(sessionSettings);
			FileLogFactory fileLogFactory = new FileLogFactory(sessionSettings);
			DefaultMessageFactory defaultMessageFactory = new DefaultMessageFactory();

			if (hasInitiator) {
				this.socketInitiator = new SocketInitiator(this, fileStoreFactory, sessionSettings, fileLogFactory,
						defaultMessageFactory);
			}

			if (hasAcceptor) {
				this.socketAcceptor = new SocketAcceptor(this, fileStoreFactory, sessionSettings, fileLogFactory,
						defaultMessageFactory);
			}
		} catch (ConfigError ex) {
			log.error(ex.getMessage(), ex);
		}
	}


	public QuickFix42Connection(String sessionsFile, boolean hasInitiator, boolean hasAcceptor, String logs, String store, String dic) {

		this.sessionsFile = sessionsFile;

		try {

			SessionSettings sessionSettings = new SessionSettings(sessionsFile);
			sessionSettings.setString("FileStorePath", store);
			sessionSettings.setString("FileLogPath", logs);
			sessionSettings.setString("DataDictionary", dic);

			FileStoreFactory fileStoreFactory = new FileStoreFactory(sessionSettings);
			FileLogFactory fileLogFactory = new FileLogFactory(sessionSettings);
			DefaultMessageFactory defaultMessageFactory = new DefaultMessageFactory();

			if (hasInitiator) {
				this.socketInitiator = new SocketInitiator(this, fileStoreFactory, sessionSettings, fileLogFactory,
						defaultMessageFactory);
			}

			if (hasAcceptor) {
				this.socketAcceptor = new SocketAcceptor(this, fileStoreFactory, sessionSettings, fileLogFactory,
						defaultMessageFactory);
			}

		} catch (Exception ex) {
			log.error(ex.getMessage(), ex);
		}
	}


	public DataDictionary getDictionary(String beginString, String senderCompID, String targetCompID)
			throws Exception {
		return getDictionary(beginString, senderCompID, targetCompID);
	}

	public String getSessionsFile() {
		return sessionsFile;
	}

	public void startApplication() {
		try {
			if (this.socketInitiator != null) {
				this.socketInitiator.start();
			}

			if (this.socketAcceptor != null) {
				this.socketAcceptor.start();
			}

			Thread.sleep(SLEEP_TIME);
		} catch (ConfigError | InterruptedException ex) {
			log.error(ex.getMessage(), ex);
		}
	}

	public void stopApplication() {
		try {
			if (this.socketInitiator != null) {
				this.socketInitiator.stop();
			}

			if (this.socketAcceptor != null) {
				this.socketAcceptor.stop();
			}

			Thread.sleep(SLEEP_TIME);
		} catch (Exception ex) {
			log.error(ex.getMessage(), ex);
		}
	}

	@Override
	public void toAdmin(Message message, SessionID sessionID) {
		try {

		} catch (Exception ex) {
			log.error(ex.getMessage(), ex);
		}
	}

	@Override
	public void fromAdmin(Message message, SessionID sessionID) throws FieldNotFound, IncorrectDataFormat, IncorrectTagValue, RejectLogon {
		try {

			crack(message, sessionID);
		} catch (UnsupportedMessageType ex) {
			log.error(ex.getMessage(), ex);
		}
	}

	@Override
	public void fromApp(Message message, SessionID sessionID)
			throws FieldNotFound, IncorrectDataFormat, IncorrectTagValue, UnsupportedMessageType {
		try {
			crack(message, sessionID);
		} catch (UnsupportedMessageType ex) {
			log.error(ex.getMessage(), ex);
		}
	}

	@Override
	public void toApp(Message message, SessionID sessionID) throws DoNotSend {

	}

}
