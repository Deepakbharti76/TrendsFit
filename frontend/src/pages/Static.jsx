import { Link } from "react-router-dom";
import { useConfig } from "../lib/AppContext";
import { useSeo } from "../lib/seo";

function Page({ title, children, desc }) {
  useSeo({ title, description: desc });
  return (
    <div className="container section" style={{ maxWidth: 820 }}>
      <h1 className="display-font h2 mb-4">{title}</h1>
      <div className="static-body">{children}</div>
    </div>
  );
}

export function Faq() {
  const cfg = useConfig();
  const items = [
    [
      "How long does delivery take?",
      'Delivery dates depend on your pincode and courier. Use "Check delivery" on any product page for an estimate; the exact date is confirmed after your order ships.',
    ],
    [
      "Can I cancel my order?",
      "Orders cannot be cancelled once placed. If the size or colour is not right, you can request an exchange after delivery.",
    ],
    [
      "How does exchange work?",
      `You can request an exchange from your order page within ${cfg.exchangeWindowDays} days of delivery. After that the option closes and you will see "Exchange period expired". See the Exchange Policy for details.`,
    ],
    [
      "Which payment methods are available?",
      "Online payment through Razorpay (UPI, cards, netbanking, wallets) and Cash on Delivery for eligible orders.",
    ],
    [
      "Is my payment secure?",
      "Payments are processed by Razorpay. We never see or store your card details, and an order is confirmed only after our server verifies the payment.",
    ],
    [
      "How do I track my order?",
      "Open the link in your confirmation email, use Track Order, or log in and open My orders.",
    ],
    [
      "Do you provide a GST invoice?",
      "Our invoice is a payment receipt without GST details.",
    ],
  ];
  return (
    <Page
      title="Frequently asked questions"
      desc="Answers about delivery, payment, exchange and tracking at TrendsFit."
    >
      <div className="accordion" id="faq">
        {items.map(([q, a], i) => (
          <div className="accordion-item rounded-0" key={q}>
            <h2 className="accordion-header">
              <button
                className="accordion-button collapsed"
                data-bs-toggle="collapse"
                data-bs-target={`#f${i}`}
              >
                {q}
              </button>
            </h2>
            <div
              id={`f${i}`}
              className="accordion-collapse collapse"
              data-bs-parent="#faq"
            >
              <div className="accordion-body">{a}</div>
            </div>
          </div>
        ))}
      </div>
    </Page>
  );
}

export const ShippingPolicy = () => (
  <Page
    title="Shipping Policy"
    desc="Learn about TrendsFit delivery coverage, shipping charges, and order tracking."
  >
    <div className="policy-content">
      <p>
        <strong>Last Updated: 27 October 2026</strong>
      </p>

      <h3>1. Shipping Coverage</h3>
      <p>
        TrendsFit delivers across India through its courier partners. Delivery
        availability depends on whether your delivery pincode is serviceable.
      </p>

      <h3>2. Shipping Charges</h3>
      <p>
        <strong>Free Delivery on Every Order.</strong> We do not charge shipping
        fees on orders covered by this policy.
      </p>

      <h3>3. Estimated Delivery Time</h3>
      <p>
        Delivery time depends on your delivery pincode and courier availability.
        An estimated delivery date is shown on the product page only when our
        courier partner confirms that your pincode is serviceable.
      </p>

      <h3>4. Order Tracking</h3>
      <p>
        Once your order has been shipped, you will receive an Air Waybill (AWB)
        number and a tracking link, where supported, to check your order's
        delivery status.
      </p>
      <p>
        You can also visit our <Link to="/track-order">Track Your Order</Link>{" "}
        page to check your order status.
      </p>

      <h3>5. Pincode Serviceability</h3>
      <p>
        If our courier partner cannot deliver to your pincode, we will inform
        you before payment wherever possible. Orders for non-serviceable
        locations may not be accepted.
      </p>

      <h3>6. Delivery Delays</h3>
      <p>
        Delivery may occasionally be delayed due to weather conditions, courier
        operations, public holidays, or other circumstances beyond our control.
      </p>

      <h3>7. Shipping Support</h3>
      <p>
        If you have questions about shipping or delivery, email us at{" "}
        <a href="mailto:trendsfit.in@gmail.com">trendsfit.in@gmail.com</a> or
        visit our <Link to="/contact">Contact Us</Link> page.
      </p>
    </div>
  </Page>
);
export const ExchangePolicy = () => (
  <Page
    title="Exchange Policy"
    desc="Learn about TrendsFit's exchange rules, eligibility, and request process."
  >
    <div className="policy-content">
      <p>
        <strong>Last Updated: 27 October 2026</strong>
      </p>

      <h3>1. Exchange Period</h3>
      <p>
        Exchanges are accepted within <strong>7 days of delivery</strong>. After
        this period, the exchange option closes and the message "Exchange period
        expired" may be displayed.
      </p>

      <h3>2. Order Cancellation</h3>
      <p>
        Orders cannot be cancelled after they are placed under this policy.
        Please review your order details carefully before confirming your
        purchase.
      </p>

      <h3>3. Product Eligibility</h3>
      <p>
        To be eligible for an exchange, items must be unused, unwashed, and have
        their original tags attached.
      </p>

      <h3>4. Size and Colour Exchange</h3>
      <p>
        You can request an exchange for another size or colour of the same
        product, subject to availability and stock.
      </p>

      <h3>5. How to Request an Exchange</h3>
      <ol>
        <li>Open your order page.</li>
        <li>Select the exchange option, if available.</li>
        <li>Choose the reason for your exchange.</li>
        <li>
          Upload photos if the product is defective or different from the item
          you ordered.
        </li>
        <li>Submit your request for review.</li>
      </ol>

      <h3>6. Request Review and Updates</h3>
      <p>
        Our team will review your exchange request and may approve it, reject
        it, or ask you for additional information. Updates will be sent to your
        registered email address at each step.
      </p>

      <h3>7. Need Help?</h3>
      <p>
        For exchange-related questions, contact us at{" "}
        <a href="mailto:trendsfit.in@gmail.com">trendsfit.in@gmail.com</a> or
        visit our <Link to="/contact">Contact Us</Link> page.
      </p>
    </div>
  </Page>
);

export const Privacy = () => (
  <Page
    title="Privacy Policy"
    desc="Learn how TrendsFit collects, uses, and protects your personal information."
  >
    <div className="policy-content">
      <p>
        <strong>Last Updated: 27 October 2026</strong>
      </p>

      <p>
        At TrendsFit, we respect your privacy and are committed to protecting
        your personal information. This Privacy Policy explains how we collect,
        use, store, and protect your information when you visit our website,
        create an account, or purchase products from us.
      </p>

      <p>
        By using our website, you acknowledge that you have read this Privacy
        Policy.
      </p>

      <h3>1. Information We Collect</h3>
      <p>We may collect the following information when you use our website:</p>
      <ul>
        <li>Full name, email address, and mobile phone number.</li>
        <li>Billing and shipping address.</li>
        <li>Account information necessary to provide login access.</li>
        <li>
          Products purchased, order history, order amount, and payment status.
        </li>
        <li>Delivery details, exchange requests, and refund requests.</li>
        <li>
          IP address, browser type, device information, and website usage data,
          where applicable.
        </li>
        <li>Cookies and similar technologies, where applicable.</li>
      </ul>
      <p>
        We collect information reasonably necessary to operate our store,
        process orders, provide customer support, and improve our services.
      </p>

      <h3>2. How We Use Your Information</h3>
      <p>We may use your information to:</p>
      <ul>
        <li>Process and confirm orders.</li>
        <li>Deliver products to your shipping address.</li>
        <li>Manage payments, cancellations, exchanges, and refunds.</li>
        <li>Create and maintain customer accounts.</li>
        <li>Send order confirmations and delivery updates.</li>
        <li>Respond to customer support requests.</li>
        <li>Detect suspicious activity and protect our website.</li>
        <li>Improve our products, services, and shopping experience.</li>
        <li>
          Send promotional communications where permitted by law and, where
          required, with your consent.
        </li>
      </ul>
      <p>
        You can opt out of promotional communications at any time. Necessary
        transactional messages about your orders may still be sent.
      </p>

      <h3>3. Payments and Payment Security</h3>
      <p>
        TrendsFit may use Razorpay or another supported payment provider to
        process online payments. Payment information is handled by the relevant
        provider in accordance with its privacy and security practices.
      </p>
      <p>
        We may retain transaction IDs, payment references, payment status, and
        related information required to manage orders and refunds. Payment-card
        information handling depends on the payment integration used by our
        website.
      </p>
      <p>
        Cash on Delivery orders may require information necessary to confirm and
        deliver your order.
      </p>

      <h3>4. Sharing of Information</h3>
      <p>We do not sell your personal information to third parties.</p>
      <p>
        We may share relevant information with service providers when necessary,
        including:
      </p>
      <ul>
        <li>Courier and shipping partners for deliveries and exchanges.</li>
        <li>Payment providers for payments and refunds.</li>
        <li>Technology and hosting providers that support our website.</li>
        <li>Customer support and communication providers.</li>
        <li>
          Legal or regulatory authorities when required by applicable law.
        </li>
      </ul>
      <p>
        We share information reasonably necessary for the relevant purpose,
        subject to applicable legal requirements.
      </p>

      <h3>5. Cookies and Similar Technologies</h3>
      <p>Our website may use cookies or similar technologies to:</p>
      <ul>
        <li>Maintain login sessions.</li>
        <li>Remember shopping cart contents and preferences.</li>
        <li>Support website security.</li>
        <li>Understand website performance, where applicable.</li>
      </ul>
      <p>
        You can manage cookies through your browser settings. Disabling certain
        cookies may affect login or checkout features. Where required, consent
        will be obtained before using non-essential cookies.
      </p>

      <h3>6. Data Security</h3>
      <p>
        We use reasonable technical and organizational measures designed to
        protect personal information against unauthorized access, loss, misuse,
        alteration, or disclosure.
      </p>
      <p>
        No website or electronic storage system can be guaranteed to be
        completely secure. Please keep your account credentials confidential and
        contact us if you suspect unauthorized access.
      </p>

      <h3>7. Data Retention</h3>
      <p>
        We retain personal information for as long as reasonably necessary for
        order fulfilment, customer support, accounting, dispute resolution,
        fraud prevention, and compliance with applicable legal obligations.
      </p>
      <p>
        When information is no longer required, appropriate steps may be taken
        to delete it, anonymize it, or securely dispose of it, subject to law.
      </p>

      <h3>8. Your Privacy Rights</h3>
      <p>Subject to applicable law, you may request to:</p>
      <ul>
        <li>Access personal information we hold about you.</li>
        <li>Correct inaccurate or incomplete information.</li>
        <li>Request deletion of personal information where applicable.</li>
        <li>Withdraw consent where processing is based on consent.</li>
        <li>Opt out of promotional communications.</li>
        <li>
          Raise a complaint about how your personal information is handled.
        </li>
      </ul>
      <p>
        Certain information may need to be retained where required by law or
        necessary to resolve disputes or legal claims.
      </p>

      <h3>9. Third-Party Services and Links</h3>
      <p>
        Our website may link to third-party websites or use external services,
        including payment gateways and delivery services. Those services operate
        under their own terms and privacy policies.
      </p>
      <p>
        TrendsFit is not responsible for the independent privacy practices of
        third-party websites it does not control.
      </p>

      <h3>10. Children's Privacy</h3>
      <p>
        Our website is intended for general retail customers and is not
        specifically designed to collect personal information from children. If
        you believe personal information has been collected in circumstances
        where it is not permitted, please contact us for review.
      </p>

      <h3>11. Changes to This Privacy Policy</h3>
      <p>
        We may update this Privacy Policy to reflect changes in our business,
        website features, service providers, or legal obligations. Revised
        versions will be published on this page with an updated date.
      </p>

      <h3>12. Contact Us</h3>
      <p>
        For questions or requests regarding this Privacy Policy, contact us
        using the details below.
      </p>
      <p>
        <strong>Website:</strong> TrendsFit
        <br />
        <strong>Email:</strong>{" "}
        <a href="mailto:trendsfit.in@gmail.com">trendsfit.in@gmail.com</a>
        <br />
        <strong>WhatsApp:</strong>{" "}
        <a
          href="https://wa.me/917644832949"
          target="_blank"
          rel="noopener noreferrer"
        >
          7644832949
        </a>
        <br />
        <strong>Contact Page:</strong> <Link to="/contact">Contact Us</Link>
      </p>
      <p>
        We will review and respond to privacy-related requests in accordance
        with applicable law.
      </p>
    </div>
  </Page>
);

export const Terms = () => (
  <Page
    title="Terms & Conditions"
    desc="Please read the terms and conditions for using TrendsFit."
  >
    <div className="policy-content">
      <p>
        <strong>Last Updated: 27 October 2026</strong>
      </p>

      <p>
        Welcome to TrendsFit. These Terms & Conditions govern your access to and
        use of our website and shopping services. By using our website or
        placing an order, you agree to these terms.
      </p>

      <h3>1. About TrendsFit</h3>
      <p>
        TrendsFit is an online shopping platform for fashion products. We aim to
        provide customers with a convenient and reliable shopping experience.
      </p>

      <h3>2. Customer Accounts</h3>
      <p>
        You are responsible for providing accurate information when creating an
        account or placing an order. Please keep your login credentials
        confidential and notify us if you suspect unauthorized access.
      </p>

      <h3>3. Products and Availability</h3>
      <p>
        We make reasonable efforts to display product descriptions, images,
        colours, sizes, and prices accurately. Actual colours may vary depending
        on your device display. Product availability may change without notice.
      </p>

      <h3>4. Pricing and Offers</h3>
      <p>
        Product prices and promotional offers are displayed on the website.
        Offers may be subject to specific eligibility conditions and validity
        periods. Any applicable charges will be shown before you confirm your
        order.
      </p>

      <h3>5. Orders and Confirmation</h3>
      <p>
        After placing an order, you should review the order details and
        confirmation information. We may contact you if additional information
        is needed to process or deliver your order.
      </p>
      <p>
        An order may be subject to availability, payment verification, address
        validation, and serviceable-pincode confirmation.
      </p>

      <h3>6. Payments</h3>
      <p>
        Available payment methods will be displayed during checkout. Online
        payments may be processed through supported payment providers. Orders
        are subject to the applicable payment verification process.
      </p>

      <h3>7. Shipping and Delivery</h3>
      <p>
        Delivery coverage, shipping charges, estimated delivery times, pincode
        serviceability, and order tracking are explained in our{" "}
        <Link to="/shipping-policy">Shipping Policy</Link>.
      </p>

      <h3>8. Order Cancellation</h3>
      <p>
        Under our current cancellation policy, orders cannot be cancelled after
        they are placed. Please review your products, size, colour, quantity,
        and delivery address carefully before confirming your order.
      </p>
      <p>
        This policy does not exclude any cancellation or other rights that
        cannot lawfully be restricted under applicable law.
      </p>

      <h3>9. Exchanges</h3>
      <p>
        Eligible products may be exchanged within 7 days of delivery, subject to
        the conditions and availability described in our{" "}
        <Link to="/exchange-policy">Exchange Policy</Link>.
      </p>

      <h3>10. Refunds</h3>
      <p>
        Refund eligibility, where applicable, depends on the circumstances of
        the order, the applicable policy, and applicable law. If a refund is
        approved, the processing method and expected timeline will be
        communicated where available.
      </p>
      <p>
        Nothing in these terms removes any refund rights that cannot lawfully be
        excluded.
      </p>

      <h3>11. Website Usage</h3>
      <p>
        You must not misuse the website, attempt unauthorized access, interfere
        with its operation, submit fraudulent orders, or use the website for
        unlawful purposes.
      </p>

      <h3>12. Intellectual Property</h3>
      <p>
        Website content, branding, graphics, and other materials may be
        protected by intellectual property laws. You may not copy, reproduce, or
        commercially use such materials without appropriate authorization.
      </p>

      <h3>13. Privacy</h3>
      <p>
        Our collection and use of personal information are described in our{" "}
        <Link to="/privacy">Privacy Policy</Link>.
      </p>

      <h3>14. Third-Party Services</h3>
      <p>
        Our website may rely on third-party payment, hosting, or courier
        services. The availability and operation of those services may be
        subject to their own terms and policies.
      </p>

      <h3>15. Limitation of Liability</h3>
      <p>
        To the extent permitted by applicable law, TrendsFit is not responsible
        for losses arising from circumstances beyond its reasonable control,
        including third-party service interruptions or delivery delays. Nothing
        in these terms excludes liability that cannot lawfully be excluded.
      </p>

      <h3>16. Changes to These Terms</h3>
      <p>
        We may update these Terms & Conditions when our services, policies, or
        legal requirements change. Updated terms will be published on this page
        with a revised date.
      </p>

      <h3>17. Contact Us</h3>
      <p>
        If you have questions about these terms, contact us at{" "}
        <a href="mailto:trendsfit.in@gmail.com">trendsfit.in@gmail.com</a> or
        visit our <Link to="/contact">Contact Us</Link> page.
      </p>
    </div>
  </Page>
);

export function Contact() {
  const cfg = useConfig();

  const email = "trendsfit.in@gmail.com";
  const whatsapp = "7644832949";
  return (
    <Page
      title="Contact Us"
      desc="Contact TrendsFit for order support, delivery, exchanges and other queries."
    >
      <div className="contact-page">
        <p className="text-muted mb-4">
          We're here to help! Contact our support team for questions about your
          orders, delivery, exchanges, or products.
        </p>

        <div className="row g-4 mb-4">
          {/* Email Support */}
          <div className="col-md-6">
            <div className="border p-4 h-100">
              <div className="fs-2 mb-2">✉️</div>
              <h3 className="h5">Email Support</h3>
              <p className="text-muted">
                Send us an email for assistance with your order or any other
                query.
              </p>

              <a href={`mailto:${email}`} className="fw-semibold">
                {email}
              </a>
            </div>
          </div>

          {/* WhatsApp Support */}
          <div className="col-md-6">
            <div className="border p-4 h-100">
              <div className="fs-2 mb-2">💬</div>

              <h3 className="h5">WhatsApp Support</h3>

              <p className="text-muted">
                Contact us on WhatsApp for help with your order and
                product-related questions.
              </p>

              <a
                href="https://wa.me/917644832949"
                target="_blank"
                rel="noopener noreferrer"
                className="btn btn-tf"
              >
                Chat on WhatsApp
              </a>
            </div>
          </div>

          {/* Customer Support */}
          <div className="border p-4 mb-4">
            <h2 className="h4 mb-3">How Can We Help You?</h2>

            <ul className="mb-0">
              <li className="mb-2">Order status and delivery enquiries</li>
              <li className="mb-2">Product and size-related questions</li>
              <li className="mb-2">Exchange requests and eligibility</li>
              <li className="mb-2">Payment and order confirmation issues</li>
              <li>Account and website-related support</li>
            </ul>
          </div>

          {/* Support Link */}
          <div className="border p-4">
            <h2 className="h4">Need Help With an Existing Order?</h2>

            <p className="text-muted">
              Visit our support page to get assistance. Please keep your order
              number ready so our team can identify your order faster.
            </p>

            <Link to="/support" className="btn btn-tf">
              Visit Support
            </Link>
          </div>

          <p className="text-muted small mt-4">TrendsFit Customer Support</p>
        </div>
      </div>
    </Page>
  );
}
export function NotFound() {
  useSeo({ title: "Page not found", noindex: true });
  return (
    <div className="container section text-center">
      <h1 className="display-font display-4">404</h1>
      <p>We couldn't find that page.</p>
      <Link to="/" className="btn btn-tf">
        Back to home
      </Link>
    </div>
  );
}
