package in.trendsfit.integration;

import java.math.BigDecimal;
import java.util.List;

/** Abstraction over the courier aggregator (Shiprocket) so tests can mock it. */
public interface ShippingGateway {
    boolean configured();

    record Courier(String name, BigDecimal rate, Integer etaDays, String etd) {}

    /** Real serviceability from the provider. Empty list means "not serviceable". */
    List<Courier> serviceability(String deliveryPincode, boolean cod, BigDecimal orderValue);

    record Parcel(String orderNumber, String orderDate, String customerName, String email, String phone, String address,
                  String city, String state, String pincode, boolean cod, BigDecimal subTotal, List<ParcelItem> items) {}

    record ParcelItem(String name, String sku, int units, BigDecimal price) {}

    record Created(String srOrderId, String srShipmentId) {}

    Created createOrder(Parcel p);

    record Awb(String awb, String courier) {}

    Awb assignAwb(String srShipmentId);

    String labelUrl(String srShipmentId);

    /** Reverse pickup for an exchange. Needs live verification against the real account. */
    Created createReturn(Parcel p);
}
