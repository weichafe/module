package cl.vc.module.protocolbuff.quickfix;

import lombok.extern.slf4j.Slf4j;
import quickfix.*;
import quickfix.field.MsgType;
import quickfix.field.RawData;
import quickfix.field.RawDataLength;
import quickfix.fix44.Logon;
import quickfix.mina.acceptor.DynamicAcceptorSessionProvider;

import java.io.File;
import java.net.InetSocketAddress;
import java.util.*;
import static quickfix.Acceptor.*;

@Slf4j
public abstract class QuickFix44Connection extends MessageCracker implements Application {

    private static final long SLEEP_TIME = 2000L;
    private final String sessionsFile;
    private SocketInitiator socketInitiator = null;
    private Map<String, String> rowDatas = new HashMap<>();
    private SocketAcceptor socketAcceptor = null;
    private final Map<InetSocketAddress, List<DynamicAcceptorSessionProvider.TemplateMapping>> dynamicSessionMappings = new HashMap<>();

    public QuickFix44Connection(String sessionsFile, boolean hasInitiator, boolean hasAcceptor, String logs, String store, String dic) {

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
                this.socketInitiator = new SocketInitiator(this, fileStoreFactory, sessionSettings, fileLogFactory, defaultMessageFactory);
            }

            if (hasAcceptor) {
                this.socketAcceptor = new SocketAcceptor(this, fileStoreFactory, sessionSettings, fileLogFactory, defaultMessageFactory);

                this.configureDynamicSessions(sessionSettings, this, fileStoreFactory, fileLogFactory,
                        defaultMessageFactory);
            }
        } catch (Exception ex) {
            log.error(ex.getMessage(), ex);
        }
    }


    public QuickFix44Connection(String sessionsFile, boolean hasInitiator, boolean hasAcceptor) {
        this.sessionsFile = sessionsFile;

        try {
            SessionSettings sessionSettings = new SessionSettings(sessionsFile);
            FileStoreFactory fileStoreFactory = new FileStoreFactory(sessionSettings);
            FileLogFactory fileLogFactory = new FileLogFactory(sessionSettings);
            DefaultMessageFactory defaultMessageFactory = new DefaultMessageFactory();

            if (hasInitiator) {
                this.socketInitiator = new SocketInitiator(this, fileStoreFactory, sessionSettings, fileLogFactory, defaultMessageFactory);
            }

            if (hasAcceptor) {
                this.socketAcceptor = new SocketAcceptor(this, fileStoreFactory, sessionSettings, fileLogFactory, defaultMessageFactory);

                this.configureDynamicSessions(sessionSettings, this, fileStoreFactory, fileLogFactory,
                        defaultMessageFactory);
            }
        } catch (Exception ex) {
            log.error(ex.getMessage(), ex);
        }
    }

    private void configureDynamicSessions(SessionSettings settings, Application application,
                                          MessageStoreFactory messageStoreFactory, LogFactory logFactory, MessageFactory messageFactory)
            throws ConfigError, FieldConvertError {

        Iterator<SessionID> sectionIterator = settings.sectionIterator();
        while (sectionIterator.hasNext()) {
            SessionID sessionID = sectionIterator.next();
            if (isSessionTemplate(settings, sessionID)) {
                InetSocketAddress address = getAcceptorSocketAddress(settings, sessionID);
                getMappings(address).add(new DynamicAcceptorSessionProvider.TemplateMapping(sessionID, sessionID));
            }
        }

        for (Map.Entry<InetSocketAddress, List<DynamicAcceptorSessionProvider.TemplateMapping>> entry : dynamicSessionMappings.entrySet()) {
            this.socketAcceptor.setSessionProvider(entry.getKey(), new DynamicAcceptorSessionProvider(settings,
                    entry.getValue(), application, messageStoreFactory, logFactory, messageFactory));
        }

    }

    private List<DynamicAcceptorSessionProvider.TemplateMapping> getMappings(InetSocketAddress address) {
        return dynamicSessionMappings.computeIfAbsent(address, k -> new ArrayList<>());
    }

    private InetSocketAddress getAcceptorSocketAddress(SessionSettings settings, SessionID sessionID)
            throws ConfigError, FieldConvertError {
        String acceptorHost = "0.0.0.0";
        if (settings.isSetting(sessionID, SETTING_SOCKET_ACCEPT_ADDRESS)) {
            acceptorHost = settings.getString(sessionID, SETTING_SOCKET_ACCEPT_ADDRESS);
        }
        int acceptorPort = (int) settings.getLong(sessionID, SETTING_SOCKET_ACCEPT_PORT);

        return new InetSocketAddress(acceptorHost, acceptorPort);
    }

    private boolean isSessionTemplate(SessionSettings settings, SessionID sessionID)
            throws ConfigError, FieldConvertError {
        return settings.isSetting(sessionID, SETTING_ACCEPTOR_TEMPLATE)
                && settings.getBool(sessionID, SETTING_ACCEPTOR_TEMPLATE);
    }

    public DataDictionary getDictionary(String beginString, String senderCompID, String targetCompID)
            throws Exception {
        return getDictionary(beginString, senderCompID, targetCompID);
    }

    public DataDictionary getDictionary(SessionID sessionID) throws Exception {
        return getDataDictionary(sessionID);
    }

    public static DataDictionary getDataDictionary(SessionID sessionID) throws Exception {
        try {
            return Session.lookupSession(sessionID).getDataDictionary();
        } catch (Exception ex) {
            log.error(ex.getMessage(), ex);
        }

        return null;
    }

    public String getSessionsFile() {
        return sessionsFile;
    }

    public void addRowDataToTargetSub(String targetSub, String rowData) {
        if (rowDatas.containsKey(targetSub)) {
            return;
        }

        rowDatas.put(targetSub, rowData);
    }

    public void startApplication() throws Exception {
        try {
            if (this.socketInitiator != null) {
                this.socketInitiator.start();
            }

            if (this.socketAcceptor != null) {
                this.socketAcceptor.start();
            }

            Thread.sleep(SLEEP_TIME);
        } catch (Exception ex) {
            log.error(ex.getMessage(), ex);
        }
    }

    public void stopApplication() throws Exception {
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

            if (message.getHeader().getString(MsgType.FIELD).equals(Logon.MSGTYPE)
                    && rowDatas.containsKey(sessionID.getTargetCompID())) {
                message.getHeader().setField(new RawDataLength(rowDatas.get(sessionID.getTargetCompID()).length()));
                message.getHeader().setField(new RawData(rowDatas.get(sessionID.getTargetCompID())));
            }
        } catch (FieldNotFound ex) {
            log.error(ex.getMessage(), ex);
        }
    }

    @Override
    public void fromAdmin(Message message, SessionID sessionID)
            throws FieldNotFound, IncorrectDataFormat, IncorrectTagValue, RejectLogon {
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
        log.info("toApp Message: {}", message);
    }


}
