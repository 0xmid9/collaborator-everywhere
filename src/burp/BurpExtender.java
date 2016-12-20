package burp;

import burp.*;

import java.util.HashMap;
import java.util.HashSet;

public class BurpExtender implements IBurpExtender {
    private static final String name = "Collaborator Everywhere";
    private static final String version = "0.1";
    public static final boolean clientSideOnly = false;
    public static HashSet<String> scanned = new HashSet<>();


    @Override
    public void registerExtenderCallbacks(final IBurpExtenderCallbacks callbacks) {
        new Utilities(callbacks);
        callbacks.setExtensionName(name);

        IBurpCollaboratorClientContext collab = callbacks.createBurpCollaboratorClientContext();
        Monitor collabMonitor = new Monitor(collab);
        new Thread(collabMonitor).start();
        callbacks.registerExtensionStateListener(collabMonitor);

        callbacks.registerHttpListener(new Injector(collab));

        Utilities.out("Loaded " + name + " v" + version);
    }
}

class Monitor implements Runnable, IExtensionStateListener {
    private IBurpCollaboratorClientContext collab;
    private boolean stop = false;

    Monitor(IBurpCollaboratorClientContext collab) {
        this.collab = collab;
    }

    public void extensionUnloaded() {
        Utilities.out("Extension unloading - triggering abort");
        stop = true;
        Thread.currentThread().interrupt();
    }

    public void run() {
        try {
            while (!stop) {
                Thread.sleep(10000);
                Utilities.out("Polling");
                for (IBurpCollaboratorInteraction interaction : collab.fetchAllCollaboratorInteractions()) {
                    processInteraction(interaction);
                }
            }
        }
        catch (InterruptedException e) {
            Utilities.out("Interrupted");
        }

        Utilities.out("Shutting down collaborator monitor thread");
    }

    private void processInteraction(IBurpCollaboratorInteraction interaction) {
        Utilities.out(interaction.getProperties().toString());
    }

}


class Injector implements IHttpListener {

    private IBurpCollaboratorClientContext collab;
    private HashMap<String, Integer> idToRequestID;
    private HashMap<Integer, IHttpRequestResponse> requests;
    private int count = 0;

    Injector(IBurpCollaboratorClientContext collab) {
        this.collab = collab;
        requests = new HashMap<>();
        idToRequestID = new HashMap<>();
    }

    @Override
    public void processHttpMessage(int toolFlag, boolean messageIsRequest, IHttpRequestResponse messageInfo) {
        if (!messageIsRequest) {
            return;
        }

        Integer requestCode = count++;
        requests.put(requestCode, messageInfo);
        byte[] fixed;

        fixed = Utilities.addOrReplaceHeader(messageInfo.getRequest(), "User-Agent", "http://"+generateCollabId(requestCode));

        fixed = Utilities.addOrReplaceHeader(fixed, "X-Wap-Profile", "http://"+generateCollabId(requestCode)+"/wap.xml");

        fixed = Utilities.addOrReplaceHeader(fixed, "Contact", "user@"+generateCollabId(requestCode));

        messageInfo.setRequest(fixed);
        Utilities.out("Injected!");

        // add or overwrite: Contact, X-Forwarded-Host, Referer, User-Agent, X-Wap-Profile, Origin
    }

    private String generateCollabId(int requestCode) {
        String id = collab.generatePayload(false);
        idToRequestID.put(id, requestCode);
        return id+"."+collab.getCollaboratorServerLocation();
    }
}