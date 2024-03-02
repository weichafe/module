package cl.vc.module.protocolbuff;

import lombok.Data;

@Data
public class ConnectionsVO {

    public String host;
    public int port;
    public Boolean status;
}
