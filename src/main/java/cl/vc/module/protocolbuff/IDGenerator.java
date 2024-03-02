package cl.vc.module.protocolbuff;

import cl.vc.module.protocolbuff.mkd.MarketDataMessage;
import cl.vc.module.protocolbuff.routing.RoutingMessage;
import java.time.ZoneId;
import java.util.UUID;

public class IDGenerator {

    public static ZoneId zoneIdStgo = ZoneId.of("America/Santiago");

    public static synchronized String getID() {
        return Long.toString(UUID.randomUUID().getMostSignificantBits(), 36);
    }

    public static synchronized String getID(String prefix) {
        return prefix + getID();
    }


    public static MarketDataMessage.SecurityExchangeMarketData conversorExdestination(RoutingMessage.SecurityExchangeRouting routing){

        if(routing.equals(RoutingMessage.SecurityExchangeRouting.XSGO) || routing.equals(RoutingMessage.SecurityExchangeRouting.XSGO_OFS)){
           return MarketDataMessage.SecurityExchangeMarketData.BCS;
        } else if(routing.equals(RoutingMessage.SecurityExchangeRouting.XBOG)){
            return MarketDataMessage.SecurityExchangeMarketData.BVC;
        } else if(routing.equals(RoutingMessage.SecurityExchangeRouting.XLIM)){
            return MarketDataMessage.SecurityExchangeMarketData.BVL;
        } else if(routing.equals(RoutingMessage.SecurityExchangeRouting.IB_SMART)){
            return MarketDataMessage.SecurityExchangeMarketData.FH_IBKR;
        } else {
            return null;
        }
    }

    public static RoutingMessage.SecurityExchangeRouting conversorExdestination(MarketDataMessage.SecurityExchangeMarketData mkd){

        if(mkd.equals(MarketDataMessage.SecurityExchangeMarketData.BCS)){
            return RoutingMessage.SecurityExchangeRouting.XSGO;
        } else if(mkd.equals(MarketDataMessage.SecurityExchangeMarketData.BVC)){
            return RoutingMessage.SecurityExchangeRouting.XBOG;
        } else if(mkd.equals(MarketDataMessage.SecurityExchangeMarketData.BVL)){
            return RoutingMessage.SecurityExchangeRouting.XLIM;
        } else {
            return null;
        }
    }

    public static void main(String[] arg){
        System.out.println(getID());
    }

}
