package com.mparticle.ext.sample;

import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mixpanel.mixpanelapi.ClientDelivery;
import com.mixpanel.mixpanelapi.MessageBuilder;
import com.mixpanel.mixpanelapi.MixpanelAPI;
import com.mparticle.sdk.MessageProcessor;
import com.mparticle.sdk.model.audienceprocessing.AudienceMembershipChangeRequest;
import com.mparticle.sdk.model.audienceprocessing.AudienceMembershipChangeResponse;
import com.mparticle.sdk.model.audienceprocessing.AudienceSubscriptionRequest;
import com.mparticle.sdk.model.audienceprocessing.AudienceSubscriptionResponse;
import com.mparticle.sdk.model.eventprocessing.CustomEvent;
import com.mparticle.sdk.model.eventprocessing.ErrorEvent;
import com.mparticle.sdk.model.eventprocessing.Event;
import com.mparticle.sdk.model.eventprocessing.EventProcessingRequest;
import com.mparticle.sdk.model.eventprocessing.EventProcessingResponse;
import com.mparticle.sdk.model.eventprocessing.Identity;
import com.mparticle.sdk.model.eventprocessing.PrivacySettingChangeEvent;
import com.mparticle.sdk.model.eventprocessing.PushMessageReceiptEvent;
import com.mparticle.sdk.model.eventprocessing.PushSubscriptionEvent;
import com.mparticle.sdk.model.eventprocessing.ProductActionEvent;
import com.mparticle.sdk.model.eventprocessing.ApplicationStateTransitionEvent;
import com.mparticle.sdk.model.eventprocessing.RuntimeEnvironment;
import com.mparticle.sdk.model.eventprocessing.ScreenViewEvent;
import com.mparticle.sdk.model.eventprocessing.SessionEndEvent;
import com.mparticle.sdk.model.eventprocessing.SessionStartEvent;
import com.mparticle.sdk.model.eventprocessing.UserAttributeChangeEvent;
import com.mparticle.sdk.model.eventprocessing.UserIdentity;
import com.mparticle.sdk.model.eventprocessing.UserIdentityChangeEvent;
import com.mparticle.sdk.model.registration.Account;
import com.mparticle.sdk.model.registration.AudienceProcessingRegistration;
import com.mparticle.sdk.model.registration.EventProcessingRegistration;
import com.mparticle.sdk.model.registration.ModuleRegistrationRequest;
import com.mparticle.sdk.model.registration.ModuleRegistrationResponse;
import com.mparticle.sdk.model.registration.Permissions;
import com.mparticle.sdk.model.registration.Setting;
import com.mparticle.sdk.model.registration.TextSetting;
import com.mparticle.sdk.model.registration.UserIdentityPermission;
import org.json.JSONObject;


/**
 * Arbitrary sample extension. Typically this class would interface
 * with another library to connect to a 3rd-party API.
 * <p>
 * The two big responsibilities of a MessageProcessor are:
 * 1. Describe its capabilities and settings to mParticle
 * 2. Process batches of data sent from mParticle, typically to translate and send somewhere else.
 */
public class SampleExtension extends MessageProcessor {

    //this name will show up in the mParticle UI
    public static final String NAME = "Alooma";

    //The Alooma input token
    public static final String SETTING_TOKEN = "token";
    //The Alooma hostname <hostname>.alooma.io
    public static final String SETTING_HOSTNAME = "hostname";

    String hostname;
    String token;
    MixpanelAPI mixpanel;

    @Override
    public ModuleRegistrationResponse processRegistrationRequest(ModuleRegistrationRequest request) {
        ModuleRegistrationResponse response = new ModuleRegistrationResponse(NAME, "1.0");
        response.setDescription("Modern data plumbing.");

        //Set the permissions - the device and user identities that this service can have access to
        Permissions permissions = new Permissions();
        permissions.setUserIdentities(
                Arrays.asList(
                        new UserIdentityPermission(UserIdentity.Type.EMAIL, Identity.Encoding.RAW),
                        new UserIdentityPermission(UserIdentity.Type.CUSTOMER, Identity.Encoding.RAW),
                        new UserIdentityPermission(UserIdentity.Type.FACEBOOK, Identity.Encoding.RAW),
                        new UserIdentityPermission(UserIdentity.Type.GOOGLE, Identity.Encoding.RAW),
                        new UserIdentityPermission(UserIdentity.Type.MICROSOFT, Identity.Encoding.RAW),
                        new UserIdentityPermission(UserIdentity.Type.OTHER, Identity.Encoding.RAW),
                        new UserIdentityPermission(UserIdentity.Type.TWITTER, Identity.Encoding.RAW),
                        new UserIdentityPermission(UserIdentity.Type.YAHOO, Identity.Encoding.RAW)
                )
        );
        response.setPermissions(permissions);

        //the extension needs to define the settings it needs in order to connect to its respective service(s).
        //you can using different settings for Event Processing vs. Audience Processing, but in this case
        //we'll just use the same object, specifying that only an API key is required for each.
        List<Setting> processorSettings = Arrays.asList(
                new TextSetting(SETTING_TOKEN, "Token").setIsRequired(true),
                new TextSetting(SETTING_HOSTNAME, "Hostname").setIsRequired(true)
        );

        //specify the supported event types. you should override the parent MessageProcessor methods
        //that correlate to each of these event types.
        List<Event.Type> supportedEventTypes = Arrays.asList(
                Event.Type.APPLICATION_STATE_TRANSITION,
                Event.Type.CUSTOM_EVENT,
                Event.Type.ERROR,
                Event.Type.PRIVACY_SETTING_CHANGE,
                //Event.Type.PRODUCT_ACTION,
                Event.Type.PUSH_MESSAGE_RECEIPT,
                Event.Type.PUSH_SUBSCRIPTION,
                Event.Type.SCREEN_VIEW,
                Event.Type.SESSION_END,
                Event.Type.SESSION_START,
                Event.Type.USER_ATTRIBUTE_CHANGE,
                Event.Type.USER_IDENTITY_CHANGE
        );

        //this extension only supports event data coming from Android and iOS devices
        List<RuntimeEnvironment.Type> environments = Arrays.asList(
                RuntimeEnvironment.Type.ANDROID,
                RuntimeEnvironment.Type.IOS,
                RuntimeEnvironment.Type.UNKNOWN
                );

        //finally use all of the above to assemble the EventProcessingRegistration object and set it in the response
        EventProcessingRegistration eventProcessingRegistration = new EventProcessingRegistration()
                .setSupportedRuntimeEnvironments(environments)
                .setAccountSettings(processorSettings)
                .setSupportedEventTypes(supportedEventTypes);
        response.setEventProcessingRegistration(eventProcessingRegistration);

        //Segmentation/Audience registration and processing is treated separately from Event processing
        //Audience integrations are configured separately in the mParticle UI
        //Customers can configure a different set of account-level settings (such as API key here), and
        //Segment-level settings (Mailing List ID here).
        List<Setting> subscriptionSettings = new LinkedList<>();

        AudienceProcessingRegistration audienceRegistration = new AudienceProcessingRegistration()
                .setAccountSettings(processorSettings)
                .setAudienceSubscriptionSettings(subscriptionSettings);
        response.setAudienceProcessingRegistration(audienceRegistration);
        return response;
    }

    /**
     * When a MessageProcessor is given a batch of data/events, it will first call this method.
     * This is a good time to do some setup. For example since a given batch will all be for the same device,
     * you could contact the server once here and make sure that that device/user exists in the system, rather than
     * doing that every time one of the more specific methods (ie processCustomEvent) is called.
     */
    @Override
    public EventProcessingResponse processEventProcessingRequest(EventProcessingRequest request) throws IOException {
        //do some setup, then call super. if you don't call super, you'll effectively short circuit
        //the whole thing, which isn't really fun for anyone.
        Account account = request.getAccount();
        hostname = account.getStringSetting(SETTING_HOSTNAME, true, null);
        token = account.getStringSetting(SETTING_TOKEN, true, null);
        mixpanel = new MixpanelAPI("https://"+hostname+".alooma.io/track/"+token, "https://"+hostname+".alooma.io/track"+token);
        return super.processEventProcessingRequest(request);
    }

    @Override
    public void processApplicationStateTransitionEvent(ApplicationStateTransitionEvent event) throws IOException {
        sendEvent(event);
        super.processApplicationStateTransitionEvent(event);
    }

    @Override
    public void processCustomEvent(CustomEvent event) throws IOException {
        sendEvent(event);
        super.processCustomEvent(event);
    }

    @Override
    public void processErrorEvent(ErrorEvent event) throws IOException {
        sendEvent(event);
        super. processErrorEvent(event);
    }

    @Override
    public void processPrivacySettingChangeEvent(PrivacySettingChangeEvent event) throws IOException {
        sendEvent(event);
        super.processPrivacySettingChangeEvent(event);
    }

    @Override
    public void processPushMessageReceiptEvent(PushMessageReceiptEvent event) throws IOException {
        sendEvent(event);
        super.processPushMessageReceiptEvent(event);
    }

    @Override
    public void processPushSubscriptionEvent(PushSubscriptionEvent event) throws IOException {
        sendEvent(event);
        super.processPushSubscriptionEvent(event);
    }

    @Override
    public void processScreenViewEvent(ScreenViewEvent event) throws IOException {
        sendEvent(event);
        super.processScreenViewEvent(event);
    }

    @Override
    public void processSessionStartEvent(SessionStartEvent event) throws IOException {
        sendEvent(event);
        super.processSessionStartEvent(event);
    }

    @Override
    public void processSessionEndEvent(SessionEndEvent event) throws IOException {
        sendEvent(event);
        super.processSessionEndEvent(event);
    }

    @Override
    public void processUserAttributeChangeEvent(UserAttributeChangeEvent event) throws IOException {
        sendEvent(event);
        super.processUserAttributeChangeEvent(event);
    }

    @Override
    public void processUserIdentityChangeEvent(UserIdentityChangeEvent event) throws IOException {
        sendEvent(event);
        super.processUserIdentityChangeEvent(event);
    }

    @Override
    public AudienceMembershipChangeResponse processAudienceMembershipChangeRequest(AudienceMembershipChangeRequest request) throws IOException {
        return super.processAudienceMembershipChangeRequest(request);
    }

    @Override
    public AudienceSubscriptionResponse processAudienceSubscriptionRequest(AudienceSubscriptionRequest request) throws IOException {
        return super.processAudienceSubscriptionRequest(request);
    }

    private void sendEvent(Event event) throws IOException{
        ClientDelivery delivery = new ClientDelivery();
        delivery.addMessage(new JSONObject(event));
        mixpanel.deliver(delivery);
    }
}
