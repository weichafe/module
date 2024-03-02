package cl.vc.module.protocolbuff.akka;

import akka.actor.ActorRef;
import akka.event.japi.SubchannelEventBus;
import cl.vc.module.protocolbuff.mkd.MarketDataMessage;

public class MessageEventBus extends SubchannelEventBus<Envelope, ActorRef, String> {

	@Override
	public akka.util.Subclassification<String> subclassification() {
		return new Subclassification();
	}

	@Override
	public String classify(Envelope event) {
		return event.getTopic();
	}

	@Override
	public void publish(Envelope event, ActorRef subscriber) {
		subscriber.tell(event.getPayload(), ActorRef.noSender());
	}

}
