package tukano.impl.kafka;


import tukano.impl.kafka.lib.KafkaSubscriber;
import tukano.impl.kafka.lib.KafkaUtils;

import java.util.List;

public class KafkaReceiver {
	private static final String FROM_BEGINNING = "earliest";

	public static void main(String[] args) {
		KafkaUtils.createTopic(KafkaSender.TOPIC, 1, 1);
		
		var subscriber = KafkaSubscriber.createSubscriber(KafkaSender.KAFKA_BROKERS, List.of(KafkaSender.TOPIC), FROM_BEGINNING);

		subscriber.start(true, (r) -> {
			System.out.printf("Topic: %s, offset: %d, value: %s\n", r.topic(), r.offset(), r.value());
		});
	}
}
