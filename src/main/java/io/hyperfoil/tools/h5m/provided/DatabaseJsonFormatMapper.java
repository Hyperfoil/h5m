package io.hyperfoil.tools.h5m.provided;

import io.hyperfoil.tools.jjq.jakarta.JqValueFormatMapper;
import io.quarkus.hibernate.orm.JsonFormat;
import io.quarkus.hibernate.orm.PersistenceUnitExtension;
import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Hibernate FormatMapper that uses jjq's JqValue parser and serializer
 * instead of Jackson's ObjectMapper. This eliminates the Jackson
 * serialization/deserialization overhead for JSON column operations.
 */
@ApplicationScoped
@PersistenceUnitExtension
@JsonFormat
@RegisterForReflection
public class DatabaseJsonFormatMapper extends JqValueFormatMapper {
}
