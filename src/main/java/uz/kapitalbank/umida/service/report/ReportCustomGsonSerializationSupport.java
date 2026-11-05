package uz.kapitalbank.umida.service.report;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import io.jmix.core.DataManager;
import io.jmix.core.Entity;
import io.jmix.core.entity.EntityValues;
import io.jmix.reports.converter.ReportGsonSerializationSupport;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.lang.Nullable;
import uz.kapitalbank.umida.entity.dict.DictDataDomain;
import uz.kapitalbank.umida.entity.dict.DictDataProduct;
import uz.kapitalbank.umida.entity.orgstructure.OrgStructureSubdivision;
import uz.kapitalbank.umida.entity.report.ReportCustom;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * The serialized structure of a report, with the domains, the data products and the business owner kept
 * away from the object cache of the add-on.
 *
 * <p>The add-on remembers the entities it has met in one map by the bare id, which only works for UUIDs. A
 * data product with the id of a domain comes back from that map as the domain, or the other way around:
 * the report fails to save with a {@link ClassCastException}, or gets the wrong classification. So these
 * attributes are excluded from the serialization of the add-on and handled here.
 *
 * <p>The JSON stays the same, {@code {"metaClass": ..., "id": ...}}, so the structures stored earlier and the
 * exported reports read as before. A reference needs nothing but the id: the add-on reads the structure
 * for the bands on every load, and the import saves the report with the references it gets here.
 */
public class ReportCustomGsonSerializationSupport extends ReportGsonSerializationSupport {

    private static final Set<String> CLASSIFICATION_REFERENCES =
            Set.of("domain", "secondaryDomains", "dataProducts", "businessOwner");

    private final DataManager dataManager;

    public ReportCustomGsonSerializationSupport(BeanFactory beanFactory) {
        super(beanFactory);
        this.dataManager = beanFactory.getBean(DataManager.class);
    }

    @Override
    protected boolean exclude(Class objectClass, String propertyName) {
        return super.exclude(objectClass, propertyName)
                || ReportCustom.class.isAssignableFrom(objectClass) && CLASSIFICATION_REFERENCES.contains(propertyName);
    }

    @Override
    protected void writeFields(JsonWriter out, Entity entity) throws IOException {
        super.writeFields(out, entity);
        if (entity instanceof ReportCustom) {
            for (String property : CLASSIFICATION_REFERENCES) {
                if (entityStates.isLoaded(entity, property)) {
                    Object value = EntityValues.getValue(entity, property);
                    writeClassification(out, property, value);
                }
            }
        }
    }

    private void writeClassification(JsonWriter out, String property, @Nullable Object value) throws IOException {
        if (value instanceof Collection<?> references) {
            out.name(property);
            out.beginArray();
            for (Object reference : references) {
                writeReference(out, reference);
            }
            out.endArray();
        } else if (value != null) {
            out.name(property);
            writeReference(out, value);
        }
    }

    private void writeReference(JsonWriter out, Object reference) throws IOException {
        out.beginObject();
        out.name("metaClass");
        out.value(metadata.getClass(reference).getName());
        out.name("id");
        out.value(String.valueOf(EntityValues.getId(reference)));
        out.endObject();
    }

    @Override
    protected void readUnresolvedProperty(Entity entity, String propertyName, JsonReader in) throws IOException {
        if (entity instanceof ReportCustom report && CLASSIFICATION_REFERENCES.contains(propertyName)) {
            switch (propertyName) {
                case "domain" -> report.setDomain(readReference(in, DictDataDomain.class, Integer::valueOf));
                case "secondaryDomains" -> report.setSecondaryDomains(readReferences(in, DictDataDomain.class));
                case "dataProducts" -> report.setDataProducts(readReferences(in, DictDataProduct.class));
                // the subdivisions have the string ids of HR
                default -> report.setBusinessOwner(readReference(in, OrgStructureSubdivision.class, id -> id));
            }
        } else {
            super.readUnresolvedProperty(entity, propertyName, in);
        }
    }

    private <E> List<E> readReferences(JsonReader in, Class<E> referenceClass) throws IOException {
        List<E> references = new ArrayList<>();
        in.beginArray();
        while (in.hasNext()) {
            references.add(readReference(in, referenceClass, Integer::valueOf));
        }
        in.endArray();
        return references;
    }

    /** The structures stored earlier also have the attributes of the reference here: only the id matters. */
    private <E> E readReference(JsonReader in, Class<E> referenceClass, Function<String, Object> idParser)
            throws IOException {
        Object id = null;
        in.beginObject();
        while (in.hasNext()) {
            if ("id".equals(in.nextName())) {
                id = idParser.apply(in.nextString());
            } else {
                in.skipValue();
            }
        }
        in.endObject();
        return dataManager.getReference(referenceClass, id);
    }
}
