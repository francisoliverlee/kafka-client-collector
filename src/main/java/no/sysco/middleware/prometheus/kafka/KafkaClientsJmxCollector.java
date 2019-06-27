package no.sysco.middleware.prometheus.kafka;

import io.prometheus.client.Collector;
import io.prometheus.client.GaugeMetricFamily;
import io.prometheus.client.hotspot.StandardExports;
import no.sysco.middleware.prometheus.kafka.domain.KafkaClientsDomain;
import org.apache.kafka.common.MetricName;
import org.apache.kafka.common.metrics.JmxReporter;

import javax.management.*;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;


public class KafkaClientsJmxCollector extends Collector {
    private static final Logger LOGGER = Logger.getLogger(KafkaClientsJmxCollector.class.getName());

    private MBeanServer mBeanServer;
    private Set<MetricName> metricNames;

    public KafkaClientsJmxCollector(Set<MetricName> metricNames) {
        this(metricNames, ManagementFactory.getPlatformMBeanServer());
    }

    public KafkaClientsJmxCollector(Set<MetricName> metricNames, MBeanServer mBeanServer) {
        this.metricNames = metricNames;
        this.mBeanServer = mBeanServer;
    }


    /**
     * standard JMX MBean name in the following format domainName:type=metricType,key1=val1,key2=val2
     * example:
     *  String objectNameWithDomain = "kafka.producer" + ":type=" + "producer-metrics" + ",client-id="+clientId;
     * */
    private ObjectName getObjectNameFromString(final String domainName, final String objectName) {
        String objectNameWithDomain = domainName + ":" + "type" + "=" + objectName + ",*";
        ObjectName responseObjectName = null;
        try {
            ObjectName mbeanObjectName = new ObjectName(objectNameWithDomain);
            Set<ObjectName> objectNames = mBeanServer.queryNames(mbeanObjectName, null);
            for (ObjectName object: objectNames) {
                responseObjectName = object;
            }
        } catch (MalformedObjectNameException mfe) {
            throw new IllegalArgumentException(mfe.getMessage());
        }
        return responseObjectName;
    }

    @SuppressWarnings("unchecked")
    public <T extends Number> T getMBeanAttributeValue(final String domainName, final String mBeanObjectName, final String attribute, final Class<T> returnType) {
        ObjectName objectName = getObjectNameFromString(domainName, mBeanObjectName);
        if (objectName == null) {
            // This also indicates that the mbeanObject is not registered.
            // Check to see if it is an exceptions-$class object
            if (mBeanObjectName.startsWith("exceptions")) {
                // The exceptions object is not added to the MBeanServer, as there
                // were no HTTP client exceptions raised yet. Return 0.
                if (returnType.equals(Double.class)) {
                    return (T) Double.valueOf(0d);
                } else if (returnType.equals(Long.class)) {
                    return (T) Long.valueOf(0L);
                }
            }
            String message = "Requested MBean Object not found";
            throw new IllegalArgumentException(message);
        }

        Object value;
        try {
            System.out.println("object name: "+ objectName + ", attribute: " + attribute);
            value = mBeanServer.getAttribute(objectName, attribute);
            final Number number;

            if (value instanceof Number) {
                number = (Number) value;
            } else {
                try {
                    number = Double.parseDouble(value.toString());
                } catch (NumberFormatException e) {
                    String message = "Failed to parse attribute value to number";
                    System.out.println(e);
                    throw new IllegalArgumentException(message);
                }
            }

            if (returnType.equals(number.getClass())) {
                return (T)number;
            } else if (returnType.equals(Short.class)) {
                return (T)Short.valueOf(number.shortValue());
            } else if (returnType.equals(Integer.class)) {
                return (T)Integer.valueOf(number.intValue());
            } else if (returnType.equals(Long.class)) {
                return (T)Long.valueOf(number.longValue());
            } else if (returnType.equals(Float.class)) {
                return (T)Float.valueOf(number.floatValue());
            } else if (returnType.equals(Double.class)) {
                return (T)Double.valueOf(number.doubleValue());
            } else if (returnType.equals(Byte.class)) {
                return (T)Byte.valueOf(number.byteValue());
            }
        } catch (AttributeNotFoundException | InstanceNotFoundException | ReflectionException | MBeanException e) {
            String message;
            if (e instanceof AttributeNotFoundException) {
                message = "The specified attribute does not exist or cannot be retrieved";
            } else if (e instanceof  InstanceNotFoundException) {
                message = "The specified MBean does not exist in the repository.";
            } else if (e instanceof MBeanException) {
                message = "Failed to retrieve the attribute from the MBean Server.";
            } else {
                message = "The requested operation is not supported by the MBean Server ";
            }

            throw new IllegalArgumentException(message);
        }
        return null;
    }

    public boolean validateMbeanObject(final String domainName, final String objectName) {
        ObjectName mbeanObject = getObjectNameFromString(domainName, objectName);
        if (mbeanObject == null) {
            String message = "Requested mbean object is not registered with the Platform MBean Server";
            throw new IllegalArgumentException(message);
        }
        MBeanServer mBeanServer = ManagementFactory.getPlatformMBeanServer();
        return mBeanServer.isRegistered(mbeanObject);
    }


    String formatMetricName(final MetricName metricName) {
        String groupName = metricName.group().replace("-","_");
        String name = metricName.name().replace("-","_");
        return groupName + "_" + name;
    }


    public List<MetricFamilySamples> collect() {
        List<MetricFamilySamples> mfs = new ArrayList<MetricFamilySamples>();
        for (MetricName metricName : metricNames) {
            if ("producer-metrics".contains(metricName.group())) {
                mfs.add(new GaugeMetricFamily(
                        formatMetricName(metricName),
                        metricName.description(),
                        getMBeanAttributeValue(KafkaClientsDomain.KAFKA_PRODUCER_MXBEAN, metricName.group(), metricName.name(), Double.class)
                ));
            }
        }
        return mfs;
    }
}
