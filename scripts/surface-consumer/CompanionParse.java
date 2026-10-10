// surface-expect: port=pass
import com.squareup.wire.schema.Location;
import com.squareup.wire.schema.internal.parser.ProtoFileElement;
import com.squareup.wire.schema.internal.parser.ProtoParser;

/** The call Apicurio makes at ProtobufSchemaParser.java:46. */
public class CompanionParse {
  static ProtoFileElement use(Location location, String text) {
    return ProtoParser.Companion.parse(location, text);
  }
}
