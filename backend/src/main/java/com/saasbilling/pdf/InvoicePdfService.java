package com.saasbilling.pdf;

import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.lowagie.text.pdf.draw.LineSeparator;
import com.saasbilling.entity.*;
import com.saasbilling.exception.ResourceNotFoundException;
import com.saasbilling.repository.BusinessRepository;
import com.saasbilling.repository.BusinessSettingsRepository;
import com.saasbilling.repository.BranchRepository;
import com.saasbilling.repository.CustomerRepository;
import com.saasbilling.storage.FileStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.time.format.DateTimeFormatter;

/**
 * Renders the layout described in spec section 20: business header with
 * logo/GSTIN, invoice/customer details, an item table, the tax/discount
 * breakdown, and a payment-status footer. Business settings (spec
 * section 21) control the prefix/notes/terms shown - not hardcoded here.
 */
@Service
public class InvoicePdfService {

    private static final Logger log = LoggerFactory.getLogger(InvoicePdfService.class);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy");
    private static final Color BRAND_COLOR = new Color(37, 99, 235); // matches --color-primary in the frontend
    private static final Color LIGHT_GREY = new Color(245, 246, 248);

    private final BusinessRepository businessRepository;
    private final CustomerRepository customerRepository;
    private final BusinessSettingsRepository businessSettingsRepository;
    private final BranchRepository branchRepository;
    private final FileStorageService fileStorageService;

    public InvoicePdfService(BusinessRepository businessRepository,
                              CustomerRepository customerRepository,
                              BusinessSettingsRepository businessSettingsRepository,
                              BranchRepository branchRepository,
                              FileStorageService fileStorageService) {
        this.businessRepository = businessRepository;
        this.customerRepository = customerRepository;
        this.businessSettingsRepository = businessSettingsRepository;
        this.branchRepository = branchRepository;
        this.fileStorageService = fileStorageService;
    }

    public byte[] generate(Invoice invoice) {
        Business business = businessRepository.findById(invoice.getBusinessId())
                .orElseThrow(() -> new ResourceNotFoundException("Business not found"));
        Customer customer = customerRepository.findByIdAndBusinessIdAndBranchId(
                        invoice.getCustomerId(), invoice.getBusinessId(), invoice.getBranchId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));
        var branch = branchRepository.findByIdAndBusinessId(invoice.getBranchId(), invoice.getBusinessId())
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found"));
        BusinessSettings settings = businessSettingsRepository.findByBranchId(invoice.getBranchId()).orElse(null);
        PrintStyle style = PrintStyle.from(settings != null ? settings.getInvoiceTemplate() : null);

        try {
            Document document = new Document(PageSize.A4, 36, 36, 36, 36);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            PdfWriter.getInstance(document, out);
            document.open();

            addHeader(document, business, branch, style, loadBusinessLogo(business));
            addInvoiceAndCustomerDetails(document, invoice, customer);
            addItemsTable(document, invoice, style);
            addTotals(document, invoice, style);
            addPaymentStatus(document, invoice);
            addNotesAndTerms(document, invoice, settings);
            addFooter(document);

            document.close();
            return out.toByteArray();
        } catch (DocumentException e) {
            throw new IllegalStateException("Failed to generate invoice PDF", e);
        }
    }

    private void addHeader(Document document, Business business, com.saasbilling.entity.Branch branch,
                           PrintStyle style, byte[] logoBytes) throws DocumentException {
        PdfPTable header = new PdfPTable(2);
        header.setWidthPercentage(100);
        header.setWidths(new float[]{3, 2});

        Font businessNameFont = new Font(Font.HELVETICA, 18, Font.BOLD, style.accentColor());
        Font smallFont = new Font(Font.HELVETICA, 9, Font.NORMAL, Color.DARK_GRAY);

        StringBuilder addressBlock = new StringBuilder();
        appendIfPresent(addressBlock, branch.getBranchName());
        appendIfPresent(addressBlock, branch.getAddressLine());
        appendIfPresent(addressBlock, joinNonBlank(", ", branch.getCity(), branch.getState(), branch.getPincode()));
        appendIfPresent(addressBlock, branch.getPhone() == null ? null : "Phone: " + branch.getPhone());
        appendIfPresent(addressBlock, branch.getEmail() == null ? null : "Email: " + branch.getEmail());
        appendIfPresent(addressBlock, branch.getGstin() == null ? null : "GSTIN: " + branch.getGstin());

        Paragraph left = new Paragraph();
        if (logoBytes != null) {
            try {
                Image logo = Image.getInstance(logoBytes);
                logo.scaleToFit(100, 48);
                left.add(new Chunk(logo, 0, -4, true));
                left.add(Chunk.NEWLINE);
            } catch (com.lowagie.text.BadElementException | IOException e) {
                log.warn("Ignoring invalid business logo for business {}", business.getId(), e);
            }
        }
        left.add(new Chunk(business.getBusinessName() + "\n", businessNameFont));
        left.add(new Chunk(addressBlock.toString(), smallFont));

        Paragraph right = new Paragraph();
        right.setAlignment(Element.ALIGN_RIGHT);
        Font invoiceTitleFont = new Font(Font.HELVETICA, 22, Font.BOLD, style.titleColor());
        right.add(new Chunk("INVOICE\n", invoiceTitleFont));

        PdfPCell leftCell = new PdfPCell(left);
        leftCell.setBorder(Rectangle.NO_BORDER);
        PdfPCell rightCell = new PdfPCell(right);
        rightCell.setBorder(Rectangle.NO_BORDER);
        rightCell.setVerticalAlignment(Element.ALIGN_TOP);

        header.addCell(leftCell);
        header.addCell(rightCell);
        document.add(header);

        LineSeparator separator = new LineSeparator(1f, 100f, style.accentColor(), Element.ALIGN_CENTER, -2);
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

    private void addItemsTable(Document document, Invoice invoice, PrintStyle style) throws DocumentException {
        PdfPTable table = new PdfPTable(new float[]{2.6f, 1f, 1f, 1.3f, 1.1f, 1f, 1.3f});
        table.setWidthPercentage(100);
        table.setSpacingBefore(4);
        table.setSpacingAfter(12);

        Font headerFont = new Font(Font.HELVETICA, 9, Font.BOLD, style.tableHeaderTextColor());
        String[] headers = {"Item", "HSN/SAC", "Qty", "Unit Price", "Discount", "Tax %", "Amount"};
        for (String h : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(h, headerFont));
            cell.setBackgroundColor(style.tableHeaderColor());
            cell.setPadding(6);
            cell.setHorizontalAlignment(h.equals("Item") ? Element.ALIGN_LEFT : Element.ALIGN_RIGHT);
            if (style.minimal()) {
                cell.setBorder(Rectangle.BOTTOM);
                cell.setBorderColor(new Color(203, 213, 225));
            }
            table.addCell(cell);
        }

        Font cellFont = new Font(Font.HELVETICA, 9, Font.NORMAL, Color.BLACK);
        boolean alternate = false;
        for (InvoiceItem item : invoice.getItems()) {
            Color bg = alternate ? style.alternateRowColor() : Color.WHITE;
            alternate = !alternate;

            addBodyCell(table, item.getItemName(), cellFont, bg, Element.ALIGN_LEFT, style);
            addBodyCell(table, item.getHsnSacCode() == null ? "-" : item.getHsnSacCode(), cellFont, bg, Element.ALIGN_RIGHT, style);
            addBodyCell(table, formatQty(item.getQuantity()), cellFont, bg, Element.ALIGN_RIGHT, style);
            addBodyCell(table, formatMoney(item.getUnitPrice()), cellFont, bg, Element.ALIGN_RIGHT, style);
            addBodyCell(table, formatMoney(item.getDiscountAmount()), cellFont, bg, Element.ALIGN_RIGHT, style);
            addBodyCell(table, item.getTaxRatePercent().stripTrailingZeros().toPlainString() + "%", cellFont, bg, Element.ALIGN_RIGHT, style);
            addBodyCell(table, formatMoney(item.getLineTotal()), cellFont, bg, Element.ALIGN_RIGHT, style);
        }

        document.add(table);
    }

    private void addBodyCell(PdfPTable table, String text, Font font, Color bg, int align, PrintStyle style) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBackgroundColor(bg);
        cell.setPadding(6);
        cell.setHorizontalAlignment(align);
        if (style.minimal()) {
            cell.setBorder(Rectangle.BOTTOM);
            cell.setBorderColor(new Color(226, 232, 240));
        }
        table.addCell(cell);
    }

    private void addTotals(Document document, Invoice invoice, PrintStyle style) throws DocumentException {
        PdfPTable wrapper = new PdfPTable(2);
        wrapper.setWidthPercentage(100);
        wrapper.setWidths(new float[]{3, 2});

        PdfPCell spacer = new PdfPCell(new Phrase(""));
        spacer.setBorder(Rectangle.NO_BORDER);
        wrapper.addCell(spacer);

        PdfPTable totals = new PdfPTable(2);
        totals.setWidthPercentage(100);

        addTotalRow(totals, "Subtotal", invoice.getSubtotal(), false, style);
        if (invoice.getItemDiscountTotal().compareTo(BigDecimal.ZERO) > 0) {
            addTotalRow(totals, "Item discounts", invoice.getItemDiscountTotal().negate(), false, style);
        }
        addTotalRow(totals, "Taxable amount", invoice.getTaxableAmount(), false, style);
        if (invoice.getCgstAmount().compareTo(BigDecimal.ZERO) > 0) {
            addTotalRow(totals, "CGST", invoice.getCgstAmount(), false, style);
            addTotalRow(totals, "SGST", invoice.getSgstAmount(), false, style);
        }
        if (invoice.getIgstAmount().compareTo(BigDecimal.ZERO) > 0) {
            addTotalRow(totals, "IGST", invoice.getIgstAmount(), false, style);
        }
        if (invoice.getAdditionalDiscountAmount().compareTo(BigDecimal.ZERO) > 0) {
            addTotalRow(totals, "Additional discount", invoice.getAdditionalDiscountAmount().negate(), false, style);
        }
        addTotalRow(totals, "Grand Total", invoice.getGrandTotal(), true, style);

        PdfPCell totalsCell = new PdfPCell(totals);
        totalsCell.setBorder(Rectangle.NO_BORDER);
        wrapper.addCell(totalsCell);

        document.add(wrapper);
    }

    private void addTotalRow(PdfPTable table, String label, BigDecimal amount, boolean bold, PrintStyle style) {
        Font labelFont = bold ? new Font(Font.HELVETICA, 11, Font.BOLD) : new Font(Font.HELVETICA, 9, Font.NORMAL, Color.DARK_GRAY);
        Font amountFont = bold ? new Font(Font.HELVETICA, 11, Font.BOLD, style.accentColor()) : new Font(Font.HELVETICA, 9, Font.NORMAL, Color.BLACK);

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

    private byte[] loadBusinessLogo(Business business) {
        String logoUrl = business.getLogoUrl();
        if (logoUrl == null || logoUrl.isBlank()) {
            return null;
        }

        try {
            String path = URI.create(logoUrl).getPath();
            String marker = "/" + business.getId() + "/logos/";
            int markerIndex = path == null ? -1 : path.lastIndexOf(marker);
            if (markerIndex < 0) {
                return null;
            }

            String fileName = path.substring(markerIndex + marker.length());
            if (!fileName.matches("[A-Za-z0-9-]+\\.(png|jpg|webp)")) {
                return null;
            }
            return fileStorageService.read(business.getId(), "logos", fileName).orElse(null);
        } catch (IllegalArgumentException e) {
            log.warn("Ignoring invalid business logo URL for business {}", business.getId(), e);
            return null;
        }
    }

    private record PrintStyle(Color accentColor, Color titleColor, Color tableHeaderColor,
                              Color tableHeaderTextColor, Color alternateRowColor, boolean minimal) {
        private static PrintStyle from(String template) {
            if ("MODERN".equalsIgnoreCase(template)) {
                Color slate = new Color(30, 41, 59);
                return new PrintStyle(slate, slate, slate, Color.WHITE, new Color(241, 245, 249), false);
            }
            if ("MINIMAL".equalsIgnoreCase(template)) {
                Color charcoal = new Color(55, 65, 81);
                return new PrintStyle(charcoal, charcoal, Color.WHITE, charcoal, Color.WHITE, true);
            }
            return new PrintStyle(BRAND_COLOR, Color.LIGHT_GRAY, BRAND_COLOR, Color.WHITE, LIGHT_GREY, false);
        }
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
