// surface-expect: port=pass
import com.squareup.wire.schema.Location;
import com.squareup.wire.schema.internal.parser.MessageElement;
import com.squareup.wire.schema.internal.parser.ProtoFileElement;

/** Property getters of the parser element model, as Kotlin exposes them to Java. */
public class ParserElementGetters {
  static String use(ProtoFileElement file, MessageElement message) {
    Location location = file.getLocation();
    return file.getPackageName() + file.getTypes().size() + file.getImports().size()
        + message.getName() + message.getFields().size() + location.getPath();
  }
}
