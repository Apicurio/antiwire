// surface-expect: port=fail owner=TASK-33.2 symbol=Companion reason=ProtoParser has no static Companion field and nested Companion class; Java callers write ProtoParser.Companion.parse(...) (the reported NoSuchFieldError)
import com.squareup.wire.schema.Location;
import com.squareup.wire.schema.internal.parser.ProtoFileElement;
import com.squareup.wire.schema.internal.parser.ProtoParser;

/** The call Apicurio makes at ProtobufSchemaParser.java:46. */
public class CompanionParse {
  static ProtoFileElement use(Location location, String text) {
    return ProtoParser.Companion.parse(location, text);
  }
}
