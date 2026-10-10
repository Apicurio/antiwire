// surface-expect: port=pass
import com.squareup.wire.schema.Location;
import com.squareup.wire.schema.Schema;
import com.squareup.wire.schema.SchemaLoader;
import java.util.Collections;

/** SchemaLoader.initRoots and loadSchema declare no checked exception upstream (no @Throws). */
public class SchemaLoadWithoutTryCatch {
  static Schema load(SchemaLoader loader, Location root) {
    loader.initRoots(Collections.singletonList(root), Collections.<Location>emptyList());
    return loader.loadSchema();
  }
}
