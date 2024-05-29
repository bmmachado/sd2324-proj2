package tukano.impl.kafka;
import tukano.impl.kafka.lib.KafkaPublisher;
import tukano.impl.kafka.lib.KafkaUtils;

public class KafkaSender {
	
	static final String TOPIC = "topic";
	//static final String KAFKA_BROKERS = "localhost:9092"; // For testing locally
	static final String KAFKA_BROKERS = "kafka:9092"; // When running in docker container...
	
	final KafkaPublisher publisher;
	
	public KafkaSender() {
		publisher = KafkaPublisher.createPublisher(KAFKA_BROKERS);
	}
	
	public long send( String msg) {
		long offset = publisher.publish(TOPIC, msg +  System.currentTimeMillis());
		if (offset >= 0)
			System.out.println("Message published with sequence number: " + offset);
		else
			System.err.println("Failed to publish message");
		return offset;
		
	}
	
	public static void main(String[] args) throws Exception {

		KafkaUtils.createTopic(TOPIC, 1, 2);
		
		var msg = args.length > 0 ? args[0] : "";


		var sender = new KafkaSender();
		for (;;) {
			sender.send(msg + System.currentTimeMillis() );
			Thread.sleep(1000);
		}
	}

}
