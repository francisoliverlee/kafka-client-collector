package no.sysco.middleware.prometheus.kafka;

import io.prometheus.client.Collector;
import no.sysco.middleware.prometheus.kafka.clients.ConsumerJmxCollector;
import no.sysco.middleware.prometheus.kafka.clients.ProducerJmxCollector;
import no.sysco.middleware.prometheus.kafka.clients.StreamJmxCollector;
import no.sysco.middleware.prometheus.kafka.template.ConsumerMetricTemplates;
import no.sysco.middleware.prometheus.kafka.template.ProducerMetricTemplates;
import no.sysco.middleware.prometheus.kafka.template.StreamMetricTemplates;

import javax.management.MBeanServer;
import java.lang.management.ManagementFactory;
import java.util.*;
import java.util.stream.Collectors;

//todo: doc
public class ClientsJmxCollector extends Collector {

    private final static List<String> KAFKA_CLIENTS_DOMAINS = Arrays.asList(
            ProducerMetricTemplates.PRODUCER_DOMAIN,
            ConsumerMetricTemplates.CONSUMER_DOMAIN,
            StreamMetricTemplates.STREAM_DOMAIN
    );

    private List<KafkaClientJmxCollector> kafkaClientJmxCollectors;

    ClientsJmxCollector() {
        this(ManagementFactory.getPlatformMBeanServer());
    }

    private ClientsJmxCollector(MBeanServer mBeanServer) {
        Map<String, Boolean> kafkaDomainFound = findKafkaDomains(mBeanServer.getDomains());
        this.kafkaClientJmxCollectors = instantiateCollectors(kafkaDomainFound);
    }

    private Map<String, Boolean> findKafkaDomains(String[] domains) {
        Map<String, Boolean> map = new HashMap<>();
        List<String> beanDomains = Arrays.asList(domains);
        for (String kafkaDomain : KAFKA_CLIENTS_DOMAINS) {
            if (beanDomains.contains(kafkaDomain)){
                map.put(kafkaDomain, true);
            } else {
                map.put(kafkaDomain, false);
            }
        }
        return map;
    }

    private List<KafkaClientJmxCollector> instantiateCollectors(Map<String, Boolean> kafkaDomainFound) {
        List<KafkaClientJmxCollector> collectors = new ArrayList<>();
        for (Map.Entry<String, Boolean> entry : kafkaDomainFound.entrySet()) {
            if (entry.getValue()){
                String domain = entry.getKey();
                if (ProducerMetricTemplates.PRODUCER_DOMAIN.equals(domain)) {
                    collectors.add(new ProducerJmxCollector());
                } else if(ConsumerMetricTemplates.CONSUMER_DOMAIN.equals(domain)) {
                    collectors.add(new ConsumerJmxCollector());
                } else if (StreamMetricTemplates.STREAM_DOMAIN.equals(domain)) {
                    collectors.add(new StreamJmxCollector());
                }
            }
        }
        return collectors;
    }

    public List<MetricFamilySamples> collect() {
        return kafkaClientJmxCollectors.stream()
                .flatMap(collector -> collector.getAllMetrics().stream())
                .collect(Collectors.toList());
    }
}
