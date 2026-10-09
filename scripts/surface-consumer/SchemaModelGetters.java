// surface-expect: port=pass
import com.squareup.wire.schema.Field;
import com.squareup.wire.schema.MessageType;
import com.squareup.wire.schema.ProtoFile;
import com.squareup.wire.schema.Schema;

/** Property getters of the linked schema model. */
public class SchemaModelGetters {
  static String use(Schema schema, ProtoFile file, MessageType type, Field field) {
    return file.getPackageName() + file.getTypes().size() + type.fields().size()
        + field.getName() + field.getTag() + field.getType() + schema.getProtoFiles().size();
  }
}
