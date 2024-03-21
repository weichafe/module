package cl.vc.module.protocolbuff.interfaces;

import cl.vc.module.protocolbuff.tcp.TransportingObjects;
public interface NettyInterface {
    void send(TransportingObjects transportingObjects);
}
