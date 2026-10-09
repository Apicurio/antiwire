// surface-expect: port=pass
import com.squareup.wire.FieldEncoding;
import com.squareup.wire.ProtoAdapter;
import com.squareup.wire.ProtoReader;
import com.squareup.wire.ProtoWriter;
import java.io.IOException;

/** A hand-written adapter using the 2- and 3-argument Class constructors (found by the Retrofit test). */
public class ProtoAdapterShortConstructors {
  static final class Two extends ProtoAdapter<String> {
    Two() {
      super(FieldEncoding.LENGTH_DELIMITED, String.class);
    }

    @Override public int encodedSize(String value) {
      return value.length();
    }

    @Override public void encode(ProtoWriter writer, String value) throws IOException {}

    @Override public String decode(ProtoReader reader) throws IOException {
      return null;
    }

    @Override public String redact(String value) {
      return value;
    }
  }

  static final class Three extends ProtoAdapter<String> {
    Three() {
      super(FieldEncoding.LENGTH_DELIMITED, String.class, "type.googleapis.com/x");
    }

    @Override public int encodedSize(String value) {
      return value.length();
    }

    @Override public void encode(ProtoWriter writer, String value) throws IOException {}

    @Override public String decode(ProtoReader reader) throws IOException {
      return null;
    }

    @Override public String redact(String value) {
      return value;
    }
  }
}
