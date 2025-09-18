
package sa.comptechco.nuxeo.common.operations.importer.service;

import org.nuxeo.ecm.platform.importer.service.DefaultImporterComponent;
import org.nuxeo.runtime.model.ComponentContext;

public class CustomDefaultImporterComponent extends DefaultImporterComponent {



    @Override
    public void activate(ComponentContext context) {
        super.importerService = new CustomDefaultImporterServiceImpl();
    }


}
