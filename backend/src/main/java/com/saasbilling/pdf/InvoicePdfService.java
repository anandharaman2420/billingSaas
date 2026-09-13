package com.saasbilling.pdf;

import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.saasbilling.entity.*;
import com.saasbilling.exception.ResourceNotFoundException;
import com.saasbilling.repository.BusinessRepository;
import com.saasbilling.repository.BusinessSettingsRepository;
import com.saasbilling.repository.CustomerRepository;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;

/**
 * Renders the layout described in spec section 20: business header with
 * logo/GSTIN, invoice/customer details, an item table, the tax/discount
 * breakdown, and a payment-status footer. Business settings (spec
 * section 21) control the prefix/notes/terms shown - not hardcoded here.
 */
@Service
public class InvoicePdfService {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy");
    private static final Color BRAND_COLOR = new Color(37, 99, 235); // matches --color-primary in the frontend
    private static final Color LIGHT_GREY = new Color(245, 246, 248);

    private final BusinessRepository businessRepository;
    private final CustomerRepository customerRepository;
    private final BusinessSettingsRepository businessSettingsRepository;

    public InvoicePdfService(BusinessRepository businessRepository,
                              CustomerRepository customerRepository,
                              BusinessSettingsRepository businessSettingsRepository) {
        this.businessRepository = businessRepository;
        this.customerRepository = customerRepository;
        this.businessSettingsRepository = businessSettingsRepository;
    }

    public byte[] generate(Invoice invoice) {
        Business business = businessRepository.findById(invoice.getBusinessId())
                .orElseThrow(() -> new ResourceNotFoundException("Business not found"));
        Customer customer = customerRepository.findByIdAndBusinessId(invoice.getCustomerId(), invoice.getBusinessId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));
        BusinessSettings settings = businessSettingsRepository.findByBusinessId(invoice.getBusinessId()).orElse(null);

        try {
            Document document = new Document(PageSize.A4, 36, 36, 36, 36);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            PdfWriter.getInstance(document, out);
            document.open();

            addHeader(document, business);
            addInvoiceAndCustomerDetails(document, invoice, customer);
            addItemsTable(document, invoice);
            addTotals(document, invoice);
            addPaymentStatus(document, invoice);
            addNotesAndTerms(document, invoice, settings);
            addFooter(document);

            document.close();
            return out.toByteArray();
        } catch (DocumentException e) {
            throw new IllegalStateException("Failed to generate invoice PDF", e);
        }
    }

    private void addHeader(Document document, Business business) throws DocumentException {
        PdfPTable header = new PdfPTable(2);
        header.setWidthPercentage(100);
        header.setWidths(new float[]{3, 2});

        Font businessNameFont = new Font(Font.HELVETICA, 18, Font.BOLD, BRAND_COLOR);
        Font smallFont = new Font(Font.HELVETICA, 9, Font.NORMAL, Color.DARK_GRAY);

        StringBuilder addressBlock = new StringBuilder();
        appendIfPresent(addressBlock, business.getAddressLine());
        appendIfPresent(addressBlock, joinNonBlank(", ", business.getCity(), business.getState(), business.getPincode()));
        appendIfPresent(addressBlock, business.getPhone() == null ? null : "Phone: " + business.getPhone());
        appendIfPresent(addressBlock, business.getEmail() == null ? null : "Email: " + business.getEmail());
        appendIfPresent(addressBlock, business.getGstin() == null ? null : "GSTIN: " + business.getGstin());

        Paragraph left = new Paragraph();
        left.add(new Chunk(business.getBusinessName() + "\n", businessNameFont));
        left.add(new Chunk(addressBlock.toString(), smallFont));

        Paragraph right = new Paragraph();
        right.setAlignment(Element.ALIGN_RIGHT);
        Font invoiceTitleFont = new Font(Font.HELVETICA, 22, Font.BOLD, Color.LIGHT_GRAY);
        right.add(new Chunk("INVOICE\n", invoiceTitleFont));

        PdfPCell leftCell = new PdfPCell(left);
        leftCell.setBorder(Rectangle.NO_BORDER);
        PdfPCell rightCell = new PdfPCell(right);
        rightCell.setBorder(Rectangle.NO_BORDER);
        rightCell.setVerticalAlignment(Element.ALIGN_TOP);

        header.addCell(leftCell);
        header.addCell(rightCell);
        document.add(header);

        LineSeparator separator = new LineSeparator(1f, 100f, BRAND_COLOR, Element.ALIGN_CENTER, -2);
        document.add(new Chunk(separator));
        document.add(Chunk.NEWLINE);
    }

    private void addInvoiceAndCustomerDetails(Document document, Invoice invoice, Customer customer) throws DocumentException {
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setSpacingAfter(16);

        Font labelFont = new Font(Font.HELVETICA, 9, Font.BOLD, Color.GRAY);
        Font valueFont = new Font(Font.HELVETICA, 10, Font.NORMAL, Color.BLACK);

        Paragraph invoiceDetails = new Paragraph();
        invoiceDetails.add(labeledLine("Invoice Number", invoice.getInvoiceNumber() == null ? "DRAFT" : invoice.getInvoiceNumber(), labelFont, valueFont));
        invoiceDetails.add(labeledLine("Invoice Date", invoice.getInvoiceDate().format(DATE_FORMAT), labelFont, valueFont));
        invoiceDetails.add(labeledLine("Due Date", invoice.getDueDate() == null ? "-" : invoice.getDueDate().format(DATE_FORMAT), labelFont, valueFont));
        invoiceDetails.add(labeledLine("Status", invoice.getStatus().name(), labelFont, valueFont));

        Paragraph customerDetails = new Paragraph();
        customerDetails.add(new Chunk("Bill To\n", labelFont));
        customerDetails.add(new Chunk(customer.getCustomerName() + "\n", new Font(Font.HELVETICA, 11, Font.BOLD)));
        StringBuilder customerAddress = new StringBuilder();
        appendIfPresent(customerAddress, customer.getAddressLine());
        appendIfPresent(customerAddress, joinNonBlank(", ", customer.getCity(), customer.getState(), customer.getPincode()));
        appendIfPresent(customerAddress, customer.getPhone() == null ? null : "Phone: " + customer.getPhone());
        appendIfPresent(customerAddress, customer.getGstin() == null ? null : "GSTIN: " + customer.getGstin());
        customerDetails.add(new Chunk(customerAddress.toString(), new Font(Font.HELVETICA, 9, Font.NORMAL, Color.DARK_GRAY)));

        PdfPCell left = new PdfPCell(customerDetails);
        left.setBorder(Rectangle.NO_BORDER);
        PdfPCell right = new PdfPCell(invoiceDetails);
        right.setBorder(Rectangle.NO_BORDER);
        right.setHorizontalAlignment(Element.ALIGN_RIGHT);

        table.addCell(left);
        table.addCell(right);
        document.add(table);
    }

    private Paragraph labeledLine(String label, String value, Font labelFont, Font valueFont) {
        Paragraph p = new Paragraph();
        p.setAlignment(Element.ALIGN_RIGHT);
        p.add(new Chunk(label + ": ", labelFont));
        p.add(new Chunk(value + "\n", valueFont));
        return p;
    }

    private void addItemsTable(Document document, Invoice invoice) throws DocumentException {
        PdfPTable table = new PdfPTable(new float[]{3.2f, 1f, 1.3f, 1.1f, 1f, 1.3f});
        table.setWidthPercentage(100);
        table.setSpacingBefore(4);
        table.setSpacingAfter(12);

        Font headerFont = new Font(Font.HELVETICA, 9, Font.BOLD, Color.WHITE);
        String[] headers = {"Item", "Qty", "Unit Price", "Discount", "Tax %", "Amount"};
        for (String h : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(h, headerFont));
            cell.setBackgroundColor(BRAND_COLOR);
            cell.setPadding(6);
            cell.setHorizontalAlignment(h.equals("Item") ? Element.ALIGN_LEFT : Element.ALIGN_RIGHT);
            table.addCell(cell);
        }

        Font cellFont = new Font(Font.HELVETICA, 9, Font.NORMAL, Color.BLACK);
        boolean alternate = false;
        for (InvoiceItem item : invoice.getItems()) {
            Color bg = alternate ? LIGHT_GREY : Color.WHITE;
            alternate = !alternate;

            addBodyCell(table, item.getItemName(), cellFont, bg, Element.ALIGN_LEFT);
            addBodyCell(table, formatQty(item.getQuantity()), cellFont, bg, Element.ALIGN_RIGHT);
            addBodyCell(table, formatMoney(item.getUnitPrice()), cellFont, bg, Element.ALIGN_RIGHT);
            addBodyCell(table, formatMoney(item.getDiscountAmount()), cellFont, bg, Element.ALIGN_RIGHT);
            addBodyCell(table, item.getTaxRatePercent().stripTrailingZeros().toPlainString() + "%", cellFont, bg, Element.ALIGN_RIGHT);
            addBodyCell(table, formatMoney(item.getLineTotal()), cellFont, bg, Element.ALIGN_RIGHT);
        }

        document.add(table);
    }

    private void addBodyCell(PdfPTable table, String text, Font font, Color bg, int align) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBackgroundColor(bg);
        cell.setPadding(6);
        cell.setHorizontalAlignment(align);
        table.addCell(cell);
    }

    private void addTotals(Document document, Invoice invoice) throws DocumentException {
        PdfPTable wrapper = new PdfPTable(2);
        wrapper.setWidthPercentage(100);
        wrapper.setWidths(new float[]{3, 2});

        PdfPCell spacer = new PdfPCell(new Phrase(""));
        spacer.setBorder(Rectangle.NO_BORDER);
        wrapper.addCell(spacer);

        PdfPTable totals = new PdfPTable(2);
        totals.setWidthPercentage(100);

        addTotalRow(totals, "Subtotal", invoice.getSubtotal(), false);
        if (invoice.getItemDiscountTotal().compareTo(BigDecimal.ZERO) > 0) {
            addTotalRow(totals, "Item discounts", invoice.getItemDiscountTotal().negate(), false);
        }
        addTotalRow(totals, "Taxable amount", invoice.getTaxableAmount(), false);
        if (invoice.getCgstAmount().compareTo(BigDecimal.ZERO) > 0) {
            addTotalRow(totals, "CGST", invoice.getCgstAmount(), false);
            addTotalRow(totals, "SGST", invoice.getSgstAmount(), false);
        }
        if (invoice.getIgstAmount().compareTo(BigDecimal.ZERO) > 0) {
            addTotalRow(totals, "IGST", invoice.getIgstAmount(), false);
        }
        if (invoice.getAdditionalDiscountAmount().compareTo(BigDecimal.ZERO) > 0) {
            addTotalRow(totals, "Additional discount", invoice.getAdditionalDiscountAmount().negate(), false);
        }
        addTotalRow(totals, "Grand Total", invoice.getGrandTotal(), true);

        PdfPCell totalsCell = new PdfPCell(totals);
        totalsCell.setBorder(Rectangle.NO_BORDER);
        wrapper.addCell(totalsCell);

        document.add(wrapper);
    }

    private void addTotalRow(PdfPTable table, String label, BigDecimal amount, boolean bold) {
        Font labelFont = bold ? new Font(Font.HELVETICA, 11, Font.BOLD) : new Font(Font.HELVETICA, 9, Font.NORMAL, Color.DARK_GRAY);
        Font amountFont = bold ? new Font(Font.HELVETICA, 11, Font.BOLD, BRAND_COLOR) : new Font(Font.HELVETICA, 9, Font.NORMAL, Color.BLACK);

        PdfPCell labelCell = new PdfPCell(new Phrase(label, labelFont));
        labelCell.setBorder(bold ? Rectangle.TOP : Rectangle.NO_BORDER);
        labelCell.setPadding(4);

        PdfPCell amountCell = new PdfPCell(new Phrase(formatMoney(amount), amountFont));
        amountCell.setBorder(bold ? Rectangle.TOP : Rectangle.NO_BORDER);
        amountCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        amountCell.setPadding(4);

        table.addCell(labelCell);
        table.addCell(amountCell);
    }

    private void addPaymentStatus(Document document, Invoice invoice) throws DocumentException {
        document.add(Chunk.NEWLINE);
        BigDecimal balance = invoice.getBalanceDue();
        String message;
        Color color;
        if (invoice.getStatus() == InvoiceStatus.CANCELLED) {
            message = "CANCELLED" + (invoice.getCancellationReason() != null ? " - " + invoice.getCancellationReason() : "");
            color = Color.RED;
        } else if (balance.compareTo(BigDecimal.ZERO) <= 0) {
            message = "PAID IN FULL";
            color = new Color(22, 163, 74);
        } else if (invoice.getAmountPaid().compareTo(BigDecimal.ZERO) > 0) {
            message = "PARTIALLY PAID - Balance due: " + formatMoney(balance);
            color = new Color(217, 119, 6);
        } else {
            message = "PAYMENT DUE: " + formatMoney(balance);
            color = new Color(220, 38, 38);
        }
        Paragraph status = new Paragraph(message, new Font(Font.HELVETICA, 11, Font.BOLD, color));
        status.setAlignment(Element.ALIGN_RIGHT);
        document.add(status);
    }

    private void addNotesAndTerms(Document document, Invoice invoice, BusinessSettings settings) throws DocumentException {
        Font labelFont = new Font(Font.HELVETICA, 9, Font.BOLD, Color.GRAY);
        Font bodyFont = new Font(Font.HELVETICA, 9, Font.NORMAL, Color.DARK_GRAY);

        String notes = invoice.getNotes() != null ? invoice.getNotes()
                : (settings != null ? settings.getInvoiceNotes() : null);
        String terms = invoice.getTerms() != null ? invoice.getTerms()
                : (settings != null ? settings.getInvoiceTerms() : null);

        if (notes != null && !notes.isBlank()) {
            document.add(Chunk.NEWLINE);
            document.add(new Paragraph("Notes", labelFont));
            document.add(new Paragraph(notes, bodyFont));
        }
        if (terms != null && !terms.isBlank()) {
            document.add(Chunk.NEWLINE);
            document.add(new Paragraph("Terms & Conditions", labelFont));
            document.add(new Paragraph(terms, bodyFont));
        }
    }

    private void addFooter(Document document) throws DocumentException {
        document.add(Chunk.NEWLINE);
        document.add(Chunk.NEWLINE);
        Paragraph thankYou = new Paragraph("Thank you for your business!",
                new Font(Font.HELVETICA, 10, Font.ITALIC, Color.GRAY));
        thankYou.setAlignment(Element.ALIGN_CENTER);
        document.add(thankYou);
    }

    private void appendIfPresent(StringBuilder builder, String value) {
        if (value != null && !value.isBlank()) {
            builder.append(value).append("\n");
        }
    }

    private String joinNonBlank(String delimiter, String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part != null && !part.isBlank()) {
                if (sb.length() > 0) sb.append(delimiter);
                sb.append(part);
            }
        }
        return sb.toString();
    }

    private String formatMoney(BigDecimal amount) {
        return "\u20B9 " + amount.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    private String formatQty(BigDecimal qty) {
        return qty.stripTrailingZeros().toPlainString();
    }
}
