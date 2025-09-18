package sa.comptechco.nuxeo.common.operations.service;

import org.apache.kafka.common.protocol.types.Field;
import org.nuxeo.ecm.automation.AutomationService;
import org.nuxeo.ecm.automation.OperationContext;
import org.nuxeo.ecm.automation.OperationException;
import org.nuxeo.ecm.core.api.CoreInstance;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.model.Component;
import org.nuxeo.runtime.model.ComponentContext;
import org.nuxeo.runtime.model.ComponentInstance;
import org.nuxeo.runtime.model.DefaultComponent;
import org.nuxeo.runtime.transaction.TransactionHelper;

import java.util.HashMap;
import java.util.Map;

public class CtsSchedulerServiceImpl extends DefaultComponent implements CtsSchedulerService  {

    private static final String CTS_CREATE_SCHEDULER_OPERATION = "AC_RunJobs_AllCompanies";


    @Override
    public void start(ComponentContext context) {
        AutomationService automationService = Framework.getService(AutomationService.class);
        System.out.println("Start running schedule for companies");
        Boolean started = TransactionHelper.startTransaction();
        try {
            System.out.println("running schedule for companies");
            CoreSession coresession = CoreInstance.getCoreSessionSystem("default");

                automationService.run(new OperationContext(coresession), CTS_CREATE_SCHEDULER_OPERATION);
            System.out.println("after running schedule for companies");
            }
            catch (Exception e){
                e.printStackTrace();
            }
        finally {
            if (started) {
                TransactionHelper.commitOrRollbackTransaction();
            }
        }


    }

    @Override
    public int getApplicationStartedOrder() {
        // should deploy after cts studio jar - studio.extensions.ksrelief-nuxeodms-comptechco.cts
        try {
            ComponentInstance component = Framework.getRuntime()
                    .getComponentInstance(
                            "studio.extensions.ksrelief-nuxeodms-comptechco.cts");


            if (component != null) {
                 component = Framework.getRuntime()
                        .getComponentInstance(
                                "studio.extensions.mop_nuxeo_cms_ecm.cts");
                if (component != null) {
                    return 10000;// return component.getApplicationStartedOrder() + 1;
                }
                else return 100000;
            } else return 100000;
        }catch (Exception e)
        {
            return  100000;
        }
    }



}
