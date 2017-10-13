package cc.blynk.server.http.web;

import cc.blynk.core.http.MediaType;
import cc.blynk.core.http.Response;
import cc.blynk.core.http.TokenBaseHttpHandler;
import cc.blynk.core.http.annotation.Consumes;
import cc.blynk.core.http.annotation.Context;
import cc.blynk.core.http.annotation.GET;
import cc.blynk.core.http.annotation.Metric;
import cc.blynk.core.http.annotation.POST;
import cc.blynk.core.http.annotation.PUT;
import cc.blynk.core.http.annotation.Path;
import cc.blynk.core.http.annotation.PathParam;
import cc.blynk.core.http.annotation.QueryParam;
import cc.blynk.server.Holder;
import cc.blynk.server.api.http.pojo.EmailPojo;
import cc.blynk.server.api.http.pojo.PinData;
import cc.blynk.server.api.http.pojo.PushMessagePojo;
import cc.blynk.server.core.BlockingIOProcessor;
import cc.blynk.server.core.dao.OrganizationDao;
import cc.blynk.server.core.dao.ReportingDao;
import cc.blynk.server.core.dao.TokenValue;
import cc.blynk.server.core.dao.UserKey;
import cc.blynk.server.core.model.DashBoard;
import cc.blynk.server.core.model.DataStream;
import cc.blynk.server.core.model.PinStorageKey;
import cc.blynk.server.core.model.auth.Session;
import cc.blynk.server.core.model.auth.User;
import cc.blynk.server.core.model.device.Device;
import cc.blynk.server.core.model.enums.PinType;
import cc.blynk.server.core.model.serialization.JsonParser;
import cc.blynk.server.core.model.web.product.Product;
import cc.blynk.server.core.model.web.product.events.Event;
import cc.blynk.server.core.model.widgets.MultiPinWidget;
import cc.blynk.server.core.model.widgets.OnePinWidget;
import cc.blynk.server.core.model.widgets.Widget;
import cc.blynk.server.core.model.widgets.notifications.Mail;
import cc.blynk.server.core.model.widgets.notifications.Notification;
import cc.blynk.server.core.model.widgets.others.rtc.RTC;
import cc.blynk.server.core.processors.EventorProcessor;
import cc.blynk.server.core.protocol.exceptions.IllegalCommandBodyException;
import cc.blynk.server.core.protocol.exceptions.NoDataException;
import cc.blynk.server.db.DBManager;
import cc.blynk.server.db.dao.descriptor.TableDataMapper;
import cc.blynk.server.notifications.mail.MailWrapper;
import cc.blynk.server.notifications.push.GCMWrapper;
import cc.blynk.utils.StringUtils;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import net.glxn.qrgen.core.image.ImageType;
import net.glxn.qrgen.javase.QRCode;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Base64;

import static cc.blynk.core.http.Response.badRequest;
import static cc.blynk.core.http.Response.ok;
import static cc.blynk.core.http.Response.redirect;
import static cc.blynk.core.http.Response.serverError;
import static cc.blynk.server.core.protocol.enums.Command.HARDWARE;
import static cc.blynk.server.core.protocol.enums.Command.HTTP_EMAIL;
import static cc.blynk.server.core.protocol.enums.Command.HTTP_GET_HISTORY_DATA;
import static cc.blynk.server.core.protocol.enums.Command.HTTP_GET_PIN_DATA;
import static cc.blynk.server.core.protocol.enums.Command.HTTP_GET_PROJECT;
import static cc.blynk.server.core.protocol.enums.Command.HTTP_IS_APP_CONNECTED;
import static cc.blynk.server.core.protocol.enums.Command.HTTP_IS_HARDWARE_CONNECTED;
import static cc.blynk.server.core.protocol.enums.Command.HTTP_NOTIFY;
import static cc.blynk.server.core.protocol.enums.Command.HTTP_QR;
import static cc.blynk.server.core.protocol.enums.Command.HTTP_UPDATE_PIN_DATA;
import static cc.blynk.server.core.protocol.enums.Command.SET_WIDGET_PROPERTY;
import static cc.blynk.server.db.dao.descriptor.TableDescriptor.KNIGHT_INSTANCE;
import static cc.blynk.utils.StringUtils.BODY_SEPARATOR;

/**
 * The Blynk Project.
 * Created by Dmitriy Dumanskiy.
 * Created on 25.12.15.
 */
@Path("")
@ChannelHandler.Sharable
public class ExternalAPIHandler extends TokenBaseHttpHandler {

    private static final Logger log = LogManager.getLogger(ExternalAPIHandler.class);
    private final BlockingIOProcessor blockingIOProcessor;
    private final OrganizationDao organizationDao;
    private final DBManager dbManager;
    private final MailWrapper mailWrapper;
    private final GCMWrapper gcmWrapper;
    private final ReportingDao reportingDao;
    private final EventorProcessor eventorProcessor;

    public ExternalAPIHandler(Holder holder, String rootPath) {
        super(holder.tokenManager, holder.sessionDao, holder.stats, rootPath);
        this.blockingIOProcessor = holder.blockingIOProcessor;
        this.organizationDao = holder.organizationDao;
        this.dbManager = holder.dbManager;
        this.mailWrapper = holder.mailWrapper;
        this.gcmWrapper = holder.gcmWrapper;
        this.reportingDao = holder.reportingDao;
        this.eventorProcessor = holder.eventorProcessor;
    }

    @GET
    @Path("/{token}/logEvent")
    public Response logEvent(@Context ChannelHandlerContext ctx,
                             @PathParam("token") String token,
                             @QueryParam("code") String eventCode,
                             @QueryParam("description") String description) {

        TokenValue tokenValue = tokenManager.getTokenValueByToken(token);

        Device device = tokenValue.device;

        Product product = organizationDao.getProductByIdOrNull(device.productId);
        if (product == null) {
            log.error("Product with id {} not exists.", device.productId);
            return (badRequest("Product not exists for device."));
        }

        if (eventCode == null) {
            log.error("Event code is not provided.");
            return (badRequest("Event code is not provided."));
        }

        Event event = product.findEventByCode(eventCode.hashCode());

        if (event == null) {
            log.error("Event with code {} not found in product {}.", eventCode, product.id);
            return badRequest("Event with code not found in product.");
        }

        blockingIOProcessor.executeDB(() -> {
            try {
                long now = System.currentTimeMillis();
                dbManager.insertEvent(device.id, event.getType(), now, eventCode.hashCode(), description);
                device.dataReceivedAt = now;
                ctx.writeAndFlush(ok(), ctx.voidPromise());
            } catch (Exception e) {
                log.error("Error inserting log event.", e);
                ctx.writeAndFlush(serverError("Error inserting log event."), ctx.voidPromise());
            }
        });

        return null;
    }

    @GET
    @Path("/{token}/project")
    @Metric(HTTP_GET_PROJECT)
    public Response getDashboard(@PathParam("token") String token) {
        TokenValue tokenValue = tokenManager.getTokenValueByToken(token);

        if (tokenValue == null) {
            log.debug("Requested token {} not found.", token);
            return Response.badRequest("Invalid token.");
        }

        return ok(tokenValue.dash.toString());
    }

    @GET
    @Path("/{token}/isHardwareConnected")
    @Metric(HTTP_IS_HARDWARE_CONNECTED)
    public Response isHardwareConnected(@PathParam("token") String token) {
        TokenValue tokenValue = tokenManager.getTokenValueByToken(token);

        if (tokenValue == null) {
            log.debug("Requested token {} not found.", token);
            return Response.badRequest("Invalid token.");
        }

        User user = tokenValue.user;
        int dashId = tokenValue.dash.id;
        int deviceId = tokenValue.device.id;

        Session session = sessionDao.userSession.get(new UserKey(user));

        return ok(session.isHardwareConnected(dashId, deviceId));
    }

    @GET
    @Path("/{token}/isAppConnected")
    @Metric(HTTP_IS_APP_CONNECTED)
    public Response isAppConnected(@PathParam("token") String token) {
        TokenValue tokenValue = tokenManager.getTokenValueByToken(token);

        if (tokenValue == null) {
            log.debug("Requested token {} not found.", token);
            return Response.badRequest("Invalid token.");
        }

        User user = tokenValue.user;
        Session session = sessionDao.userSession.get(new UserKey(user));

        return ok(tokenValue.dash.isActive && session.isAppConnected());
    }

    @GET
    @Path("/{token}/get/{pin}")
    @Metric(HTTP_GET_PIN_DATA)
    public Response getWidgetPinDataNew(@PathParam("token") String token,
                                        @PathParam("pin") String pinString) {
        return getWidgetPinData(token, pinString);
    }

    //todo old API.
    @GET
    @Path("/{token}/pin/{pin}")
    @Metric(HTTP_GET_PIN_DATA)
    public Response getWidgetPinData(@PathParam("token") String token,
                                     @PathParam("pin") String pinString) {

        TokenValue tokenValue = tokenManager.getTokenValueByToken(token);

        if (tokenValue == null) {
            log.debug("Requested token {} not found.", token);
            return Response.badRequest("Invalid token.");
        }

        User user = tokenValue.user;
        int deviceId = tokenValue.device.id;
        DashBoard dashBoard = tokenValue.dash;

        PinType pinType;
        byte pin;

        try {
            pinType = PinType.getPinType(pinString.charAt(0));
            pin = Byte.parseByte(pinString.substring(1));
        } catch (NumberFormatException | IllegalCommandBodyException e) {
            log.debug("Wrong pin format. {}", pinString);
            return Response.badRequest("Wrong pin format.");
        }

        Widget widget = dashBoard.findWidgetByPin(deviceId, pin, pinType);

        if (widget == null) {
            String value = dashBoard.pinsStorage.get(new PinStorageKey(deviceId, pinType, pin));
            if (value == null) {
                log.debug("Requested pin {} not found. User {}", pinString, user.email);
                return Response.badRequest("Requested pin doesn't exist in the app.");
            }
            return ok(JsonParser.valueToJsonAsString(value.split(StringUtils.BODY_SEPARATOR_STRING)));
        }

        return ok(widget.getJsonValue());
    }

    @GET
    @Path("/{token}/rtc")
    @Metric(HTTP_GET_PIN_DATA)
    public Response getWidgetPinData(@PathParam("token") String token) {
        TokenValue tokenValue = tokenManager.getTokenValueByToken(token);

        if (tokenValue == null) {
            log.debug("Requested token {} not found.", token);
            return Response.badRequest("Invalid token.");
        }

        User user = tokenValue.user;

        RTC rtc = tokenValue.dash.getWidgetByType(RTC.class);

        if (rtc == null) {
            log.debug("Requested rtc widget not found. User {}", user.email);
            return Response.badRequest("Requested rtc not exists in app.");
        }

        return ok(rtc.getJsonValue());
    }

    @GET
    @Path("/{token}/qr")
    @Metric(HTTP_QR)
    public Response getQR(@PathParam("token") String token) {
        TokenValue tokenValue = tokenManager.getTokenValueByToken(token);

        if (tokenValue == null) {
            log.debug("Requested token {} not found.", token);
            return Response.badRequest("Invalid token.");
        }

        DashBoard dashBoard = tokenValue.dash;

        try {
            byte[] compressed = JsonParser.gzipDashRestrictive(dashBoard);
            String qrData = "bp1" + Base64.getEncoder().encodeToString(compressed);
            byte[] qrDataBinary = QRCode.from(qrData).to(ImageType.PNG).withSize(500, 500).stream().toByteArray();
            return ok(qrDataBinary, "image/png");
        } catch (Throwable e) {
            log.error("Error generating QR. Reason : {}", e.getMessage());
            return Response.badRequest("Error generating QR.");
        }
    }

    @GET
    @Path("/{token}/data/{pin}")
    @Metric(HTTP_GET_HISTORY_DATA)
    public Response getPinHistoryData(@PathParam("token") String token,
                                      @PathParam("pin") String pinString) {
        TokenValue tokenValue = tokenManager.getTokenValueByToken(token);

        if (tokenValue == null) {
            log.debug("Requested token {} not found.", token);
            return Response.badRequest("Invalid token.");
        }

        User user = tokenValue.user;
        int dashId = tokenValue.dash.id;
        int deviceId = tokenValue.device.id;

        PinType pinType;
        byte pin;

        try {
            pinType = PinType.getPinType(pinString.charAt(0));
            pin = Byte.parseByte(pinString.substring(1));
        } catch (NumberFormatException | IllegalCommandBodyException e) {
            log.debug("Wrong pin format. {}", pinString);
            return Response.badRequest("Wrong pin format.");
        }

        //todo may be optimized
        try {
            java.nio.file.Path path = reportingDao.csvGenerator.createCSV(
                    user, dashId, deviceId, pinType, pin, deviceId);
            return redirect("/" + path.getFileName().toString());
        } catch (IllegalCommandBodyException e1) {
            log.debug(e1.getMessage());
            return Response.badRequest(e1.getMessage());
        } catch (NoDataException noData) {
            log.debug("No data for pin.");
            return Response.badRequest("No data for pin.");
        } catch (Exception e) {
            log.debug("Error getting pin data.");
            return Response.badRequest("Error getting pin data.");
        }
    }

    public Response updateWidgetProperty(String token,
                                         String pinString,
                                         String property,
                                         String... values) {
        if (values.length == 0) {
            log.debug("No properties for update provided.");
            return Response.badRequest("No properties for update provided.");
        }

        TokenValue tokenValue = tokenManager.getTokenValueByToken(token);

        if (tokenValue == null) {
            log.debug("Requested token {} not found.", token);
            return Response.badRequest("Invalid token.");
        }

        User user = tokenValue.user;
        int deviceId = tokenValue.device.id;
        DashBoard dash = tokenValue.dash;

        //todo add test for this use case
        if (!dash.isActive) {
            return Response.badRequest("Project is not active.");
        }

        PinType pinType;
        byte pin;
        try {
            pinType = PinType.getPinType(pinString.charAt(0));
            pin = Byte.parseByte(pinString.substring(1));
        } catch (NumberFormatException | IllegalCommandBodyException e) {
            log.debug("Wrong pin format. {}", pinString);
            return Response.badRequest("Wrong pin format.");
        }

        //for now supporting only virtual pins
        Widget widget = dash.findWidgetByPin(deviceId, pin, pinType);

        if (widget == null || pinType != PinType.VIRTUAL) {
            log.debug("No widget for SetWidgetProperty command.");
            return Response.badRequest("No widget for SetWidgetProperty command.");
        }

        try {
            //todo for now supporting only single property
            widget.setProperty(property, values[0]);
        } catch (Exception e) {
            log.debug("Error setting widget property. Reason : {}", e.getMessage());
            return Response.badRequest("Error setting widget property.");
        }

        Session session = sessionDao.userSession.get(new UserKey(user));
        session.sendToApps(SET_WIDGET_PROPERTY, 111, dash.id,
                deviceId, "" + pin + BODY_SEPARATOR + property + BODY_SEPARATOR + values[0]);
        return ok();
    }

    //todo it is a bit ugly right now. could be simplified by passing map of query params.
    @GET
    @Path("/{token}/update/{pin}")
    @Consumes(value = MediaType.APPLICATION_JSON)
    @Metric(HTTP_UPDATE_PIN_DATA)
    public Response updateWidgetPinDataViaGet(@Context ChannelHandlerContext ctx,
                                              @PathParam("token") String token,
                                              @PathParam("pin") String pinString,
                                              @QueryParam("value") String[] pinValues,
                                              @QueryParam("label") String labelValue,
                                              @QueryParam("labels") String labelsValue,
                                              @QueryParam("color") String colorValue,
                                              @QueryParam("onLabel") String onLabelValue,
                                              @QueryParam("offLabel") String offLabelValue,
                                              @QueryParam("isOnPlay") String isOnPlay) {

        if (pinValues != null) {
            return updateWidgetPinData(ctx, token, pinString, pinValues);
        }
        if (labelValue != null) {
            return updateWidgetProperty(token, pinString, "label", labelValue);
        }
        if (labelsValue != null) {
            return updateWidgetProperty(token, pinString, "labels", labelsValue);
        }
        if (colorValue != null) {
            return updateWidgetProperty(token, pinString, "color", colorValue);
        }
        if (onLabelValue != null) {
            return updateWidgetProperty(token, pinString, "onLabel", onLabelValue);
        }
        if (offLabelValue != null) {
            return updateWidgetProperty(token, pinString, "offLabel", offLabelValue);
        }
        if (isOnPlay != null) {
            return updateWidgetProperty(token, pinString, "isOnPlay", isOnPlay);
        }

        return Response.badRequest("Wrong request format.");
    }

    @PUT
    @Path("/{token}/update/{pin}")
    @Consumes(value = MediaType.APPLICATION_JSON)
    @Metric(HTTP_UPDATE_PIN_DATA)
    public Response updateWidgetPinDataNew(@Context ChannelHandlerContext ctx,
                                           @PathParam("token") String token,
                                           @PathParam("pin") String pinString,
                                           String[] pinValues) {
        return updateWidgetPinData(ctx, token, pinString, pinValues);
    }

    @PUT
    @Path("/{token}/pin/{pin}")
    @Consumes(value = MediaType.APPLICATION_JSON)
    @Metric(HTTP_UPDATE_PIN_DATA)
    public Response updateWidgetPinData(@Context ChannelHandlerContext ctx,
                                        @PathParam("token") String token,
                                        @PathParam("pin") String pinString,
                                        String[] pinValues) {

        if (pinValues.length == 0) {
            log.debug("No pin for update provided.");
            return Response.badRequest("No pin for update provided.");
        }

        TokenValue tokenValue = tokenManager.getTokenValueByToken(token);

        if (tokenValue == null) {
            log.debug("Requested token {} not found.", token);
            return Response.badRequest("Invalid token.");
        }

        User user = tokenValue.user;
        int dashId = tokenValue.dash.id;
        int deviceId = tokenValue.device.id;

        DashBoard dash = tokenValue.dash;

        PinType pinType;
        byte pin;

        try {
            pinType = PinType.getPinType(pinString.charAt(0));
            pin = Byte.parseByte(pinString.substring(1));
        } catch (NumberFormatException | IllegalCommandBodyException e) {
            log.debug("Wrong pin format. {}", pinString);
            return Response.badRequest("Wrong pin format.");
        }

        if (pin == 100 && pinType == PinType.VIRTUAL) {
            blockingIOProcessor.executeDB(() -> {
                try {
                    TableDataMapper tableDataMapper = new TableDataMapper(
                            KNIGHT_INSTANCE,
                            deviceId, pin, pinType, pinValues);
                    dbManager.reportingDBDao.insertDataPoint(tableDataMapper);
                    ctx.writeAndFlush(ok());
                } catch (Exception e) {
                    log.error("Error insert knight record.", e);
                    ctx.writeAndFlush(serverError("Error insert knight record."));
                }
            });
            return null;
        }

        final long now = System.currentTimeMillis();

        String pinValue = String.join(StringUtils.BODY_SEPARATOR_STRING, pinValues);

        reportingDao.process(user, dashId, deviceId, pin, pinType, pinValue, now);

        dash.update(deviceId, pin, pinType, pinValue, now);

        String body = makeBody(dash, deviceId, pin, pinType, pinValue);

        Session session = sessionDao.userSession.get(new UserKey(user));
        if (session == null) {
            log.debug("No session for user {}.", user.email);
            return Response.ok();
        }

        eventorProcessor.process(user, session, dash, deviceId, pin, pinType, pinValue, now);

        session.sendMessageToHardware(dashId, HARDWARE, 111, body, deviceId);

        if (dash.isActive) {
            session.sendToApps(HARDWARE, 111, dashId, deviceId, body);
        }

        return ok();
    }

    //todo remove later?
    @PUT
    @Path("/{token}/updateBatch/{pin}")
    @Consumes(value = MediaType.APPLICATION_JSON)
    @Metric(HTTP_UPDATE_PIN_DATA)
    public Response updateWidgetPinData(@Context ChannelHandlerContext ctx,
                                        @PathParam("token") String token,
                                        @PathParam("pin") String pinString,
                                        String[][] pinValues) {

        if (pinValues.length == 0) {
            log.debug("No pin for update provided.");
            return Response.badRequest("No pin for update provided.");
        }

        TokenValue tokenValue = tokenManager.getTokenValueByToken(token);

        if (tokenValue == null) {
            log.debug("Requested token {} not found.", token);
            return Response.badRequest("Invalid token.");
        }

        PinType pinType;
        byte pin;

        try {
            pinType = PinType.getPinType(pinString.charAt(0));
            pin = Byte.parseByte(pinString.substring(1));
        } catch (NumberFormatException | IllegalCommandBodyException e) {
            log.debug("Wrong pin format. {}", pinString);
            return Response.badRequest("Wrong pin format.");
        }

        int deviceId = tokenValue.device.id;

        //todo separate thread
        if (pin == 100 && pinType == PinType.VIRTUAL) {
            blockingIOProcessor.executeDB(() -> {
                try {
                    TableDataMapper[] tableDataMappers = new TableDataMapper[pinValues.length];
                    int i = 0;
                    for (String[] pinValue : pinValues) {
                        tableDataMappers[i++] = new TableDataMapper(KNIGHT_INSTANCE,
                                deviceId, pin, pinType,
                                pinValue);
                    }

                    dbManager.reportingDBDao.insertDataPoint(tableDataMappers);
                    ctx.writeAndFlush(ok());
                } catch (Exception e) {
                    log.error("Error insert knight record.", e);
                    ctx.writeAndFlush(serverError("Error insert knight record."));
                }
            });
        }

        return null;
    }

    @PUT
    @Path("/{token}/extra/pin/{pin}")
    @Consumes(value = MediaType.APPLICATION_JSON)
    @Metric(HTTP_UPDATE_PIN_DATA)
    public Response updateWidgetPinData(@PathParam("token") String token,
                                        @PathParam("pin") String pinString,
                                        PinData[] pinsData) {

        if (pinsData.length == 0) {
            log.debug("No pin for update provided.");
            return Response.badRequest("No pin for update provided.");
        }

        TokenValue tokenValue = tokenManager.getTokenValueByToken(token);

        if (tokenValue == null) {
            log.debug("Requested token {} not found.", token);
            return Response.badRequest("Invalid token.");
        }

        User user = tokenValue.user;
        int dashId = tokenValue.dash.id;
        int deviceId = tokenValue.device.id;

        DashBoard dash = tokenValue.dash;

        PinType pinType;
        byte pin;

        try {
            pinType = PinType.getPinType(pinString.charAt(0));
            pin = Byte.parseByte(pinString.substring(1));
        } catch (NumberFormatException | IllegalCommandBodyException e) {
            log.debug("Wrong pin format. {}", pinString);
            return Response.badRequest("Wrong pin format.");
        }

        for (PinData pinData : pinsData) {
            reportingDao.process(user, dashId, deviceId, pin, pinType, pinData.value, pinData.timestamp);
        }

        long now = System.currentTimeMillis();
        dash.update(deviceId, pin, pinType, pinsData[0].value, now);

        String body = makeBody(dash, deviceId, pin, pinType, pinsData[0].value);

        if (body != null) {
            Session session = sessionDao.userSession.get(new UserKey(user));
            if (session == null) {
                log.error("No session for user {}.", user.email);
                return Response.ok();
            }
            session.sendMessageToHardware(dashId, HARDWARE, 111, body, deviceId);

            if (dash.isActive) {
                session.sendToApps(HARDWARE, 111, dashId, deviceId, body);
            }
        }

        return Response.ok();
    }

    @POST
    @Path("/{token}/notify")
    @Consumes(value = MediaType.APPLICATION_JSON)
    @Metric(HTTP_NOTIFY)
    public Response notify(@PathParam("token") String token,
                           PushMessagePojo message) {

        TokenValue tokenValue = tokenManager.getTokenValueByToken(token);

        if (tokenValue == null) {
            log.debug("Requested token {} not found.", token);
            return Response.badRequest("Invalid token.");
        }

        User user = tokenValue.user;

        if (message == null || Notification.isWrongBody(message.body)) {
            log.debug("Notification body is wrong. '{}'", message == null ? "" : message.body);
            return Response.badRequest("Body is empty or larger than 255 chars.");
        }

        DashBoard dash = tokenValue.dash;

        if (!dash.isActive) {
            log.debug("Project is not active.");
            return Response.badRequest("Project is not active.");
        }

        Notification notification = dash.getWidgetByType(Notification.class);

        if (notification == null || notification.hasNoToken()) {
            log.debug("No notification tokens.");
            if (notification == null) {
                return Response.badRequest("No notification widget.");
            } else {
                return Response.badRequest("Notification widget not initialized.");
            }
        }

        log.trace("Sending push for user {}, with message : '{}'.", user.email, message.body);
        notification.push(gcmWrapper, message.body, dash.id);

        return Response.ok();
    }

    @POST
    @Path("/{token}/email")
    @Consumes(value = MediaType.APPLICATION_JSON)
    @Metric(HTTP_EMAIL)
    public Response email(@PathParam("token") String token,
                          EmailPojo message) {

        TokenValue tokenValue = tokenManager.getTokenValueByToken(token);

        if (tokenValue == null) {
            log.debug("Requested token {} not found.", token);
            return Response.badRequest("Invalid token.");
        }

        DashBoard dash = tokenValue.dash;

        if (dash == null || !dash.isActive) {
            log.debug("Project is not active.");
            return Response.badRequest("Project is not active.");
        }

        Mail mail = dash.getWidgetByType(Mail.class);

        if (mail == null) {
            log.debug("No email widget.");
            return Response.badRequest("No email widget.");
        }

        if (message == null
                || message.subj == null || message.subj.isEmpty()
                || message.to == null || message.to.isEmpty()) {
            log.debug("Email body empty. '{}'", message);
            return Response.badRequest("Email body is wrong. Missing or empty fields 'to', 'subj'.");
        }

        log.trace("Sending Mail for user {}, with message : '{}'.", tokenValue.user.email, message.subj);
        mail(tokenValue.user.email, message.to, message.subj, message.title);

        return Response.ok();
    }

    private void mail(String email, String to, String subj, String body) {
        blockingIOProcessor.execute(() -> {
            try {
                mailWrapper.sendText(to, subj, body);
            } catch (Exception e) {
                log.error("Error sending email from HTTP. From : '{}', to : '{}'. Reason : {}",
                        email, to, e.getMessage());
            }
        });
    }


    private static String makeBody(DashBoard dash, int deviceId, byte pin, PinType pinType, String pinValue) {
        Widget widget = dash.findWidgetByPin(deviceId, pin, pinType);
        if (widget == null) {
            return DataStream.makeHardwareBody(pinType, pin, pinValue);
        } else {
            if (widget instanceof OnePinWidget) {
                return ((OnePinWidget) widget).makeHardwareBody();
            } else {
                return ((MultiPinWidget) widget).makeHardwareBody(pin, pinType);
            }
        }
    }
}
