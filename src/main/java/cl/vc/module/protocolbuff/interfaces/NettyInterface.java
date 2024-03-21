package cl.vc.module.protocolbuff.interfaces;

import cl.vc.module.protocolbuff.session.SessionsMessage;
import cl.vc.module.protocolbuff.tcp.TransportingObjects;
import com.google.protobuf.Message;

public interface NettyInterface {
    void send(SessionsMessage.Disconnect disconnect);

    void send(TransportingObjects transportingObjects);

    void send(SessionsMessage.Connect connect);

    void send(Message message);
}
