/**
 * eGov suite of products aim to improve the internal efficiency,transparency,
   accountability and the service delivery of the government  organizations.

    Copyright (C) <2016>  eGovernments Foundation

    The updated version of eGov suite of products as by eGovernments Foundation
    is available at http://www.egovernments.org

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program. If not, see http://www.gnu.org/licenses/ or
    http://www.gnu.org/licenses/gpl.html .

    In addition to the terms of the GPL license to be adhered to in using this
    program, the following additional terms are to be complied with:

        1) All versions of this program, verbatim or modified must carry this
           Legal Notice.

        2) Any misrepresentation of the origin of the material is prohibited. It
           is required that all modified versions of this material be marked in
           reasonable ways as different from the original version.

        3) This license does not grant any rights to any user of the program
           with regards to rights under trademark law for use of the trade names
           or trademarks of eGovernments Foundation.

  In case of any queries, you can reach eGovernments Foundation at contact@egovernments.org.
 */
package org.egov.stms.notice.service;

import static org.egov.ptis.constants.PropertyTaxConstants.FILESTORE_MODULE_NAME;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.WordUtils;
import org.apache.log4j.Logger;
import org.egov.demand.model.EgDemand;
import org.egov.demand.model.EgDemandDetails;
import org.egov.demand.model.EgdmCollectedReceipt;
import org.egov.eis.service.AssignmentService;
import org.egov.eis.service.DesignationService;
import org.egov.infra.admin.master.entity.Module;
import org.egov.infra.admin.master.service.ModuleService;
import org.egov.infra.filestore.entity.FileStoreMapper;
import org.egov.infra.filestore.service.FileStoreService;
import org.egov.infra.reporting.engine.ReportOutput;
import org.egov.infra.reporting.engine.ReportRequest;
import org.egov.infra.reporting.engine.ReportService;
import org.egov.infra.utils.autonumber.AutonumberServiceBeanResolver;
import org.egov.infra.validation.exception.ValidationError;
import org.egov.infra.validation.exception.ValidationException;
import org.egov.ptis.domain.model.AssessmentDetails;
import org.egov.ptis.domain.model.OwnerName;
import org.egov.ptis.domain.service.property.PropertyExternalService;
import org.egov.stms.masters.service.FeesDetailMasterService;
import org.egov.stms.notice.entity.SewerageNotice;
import org.egov.stms.transactions.entity.SewerageApplicationDetails;
import org.egov.stms.transactions.entity.SewerageConnectionFee;
import org.egov.stms.transactions.repository.SewerageNoticeRepository;
import org.egov.stms.transactions.service.SewerageApplicationDetailsService;
import org.egov.stms.transactions.service.SewerageConnectionFeeService;
import org.egov.stms.utils.SewerageTaxUtils;
import org.egov.stms.utils.constants.SewerageTaxConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lowagie.text.Document;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfImportedPage;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.PdfWriter;

@Service
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@Transactional(readOnly = true)
public class SewerageNoticeService {

    private static final Logger LOGGER = Logger.getLogger(SewerageNoticeService.class);
    @Autowired
    private ModuleService moduleDao;
    @Autowired
    @Qualifier("fileStoreService")
    protected FileStoreService fileStoreService;
    @Autowired
    private SewerageNoticeRepository sewerageNoticeRepository;
    @Autowired
    private SewerageApplicationDetailsService sewerageApplicationDetailsService;

    private InputStream generateNoticePDF;

    @Autowired
    private ReportService reportService;

    @Autowired
    private SewerageTaxUtils sewerageTaxUtils;

    @Autowired
    private FeesDetailMasterService feesDetailMasterService;

    @Autowired
    private SewerageConnectionFeeService SewerageConnectionFeeService;

    @Autowired
    @Qualifier("parentMessageSource")
    private MessageSource stmsMessageSource;

    @Autowired
    private AssignmentService assignmentService;
    @Autowired
    private DesignationService designationService;

    @Autowired
    private AutonumberServiceBeanResolver beanResolver;
    
    public static final String ESTIMATION_NOTICE = "sewerageEstimationNotice";
    public static final String WORKORDERNOTICE = "sewerageWorkOrderNotice";
    public static final String CLOSECONNECTIONNOTICE = "sewerageCloseConnectionNotice";
    private Map<String, Object> reportParams = null;
    private ReportRequest reportInput = null;
    private ReportOutput reportOutput = null;
    private BigDecimal donationCharges = BigDecimal.ZERO;
    private BigDecimal sewerageCharges = BigDecimal.ZERO;
    private BigDecimal estimationCharges = BigDecimal.ZERO;

    public SewerageNotice findByNoticeTypeAndApplicationNumber(final String noticeType, final String applicationNumber) {
        return sewerageNoticeRepository.findByNoticeTypeAndApplicationNumber(noticeType, applicationNumber);
    }

    public List<SewerageNotice> findByNoticeType(final String noticeType) {
        return sewerageNoticeRepository.findByNoticeType(noticeType);
    }

    public SewerageNotice findByNoticeNoAndNoticeType(final String noticeNo, final String noticeType) {
        return sewerageNoticeRepository.findByNoticeNoAndNoticeType(noticeNo, noticeType);
    }

    public SewerageNotice saveEstimationNotice(final SewerageApplicationDetails sewerageApplicationDetails,
            final InputStream fileStream) {
        SewerageNotice sewerageNotice = null;

        if (sewerageApplicationDetails != null) {
            sewerageNotice = new SewerageNotice();

            String estNoticeNo = sewerageApplicationDetails.getEstimationNumber();
            buildSewerageNotice(sewerageApplicationDetails, sewerageNotice, estNoticeNo,
                    sewerageApplicationDetails.getEstimationDate(), SewerageTaxConstants.NOTICE_TYPE_ESTIMATION_NOTICE);
            final String fileName = estNoticeNo + ".pdf";
            final FileStoreMapper fileStore = fileStoreService.store(fileStream, fileName, "application/pdf",
                    SewerageTaxConstants.FILESTORE_MODULECODE);
            sewerageNotice.setFileStore(fileStore);
        }
        return sewerageNotice;
    }

    private void buildSewerageNotice(final SewerageApplicationDetails sewerageApplicationDetails,
            SewerageNotice sewerageNotice, String noticeNumber, Date noticeDate, String noticeType) {
        final Module module = moduleDao.getModuleByName(SewerageTaxConstants.MODULE_NAME);
        sewerageNotice.setModule(module);
        sewerageNotice.setApplicationNumber(sewerageApplicationDetails.getApplicationNumber());
        sewerageNotice.setNoticeType(noticeType);
        sewerageNotice.setNoticeNo(noticeNumber);
        sewerageNotice.setNoticeDate(noticeDate);
        sewerageNotice.setApplicationDetails(sewerageApplicationDetails);
    }

    public SewerageNotice saveWorkOrderNotice(final SewerageApplicationDetails sewerageApplicationDetails,
            final InputStream fileStream) {

        SewerageNotice sewerageNotice = null;
        if (sewerageApplicationDetails != null) {
            sewerageNotice = new SewerageNotice();
            String workOrederNo = sewerageApplicationDetails.getWorkOrderNumber();

            buildSewerageNotice(sewerageApplicationDetails, sewerageNotice, workOrederNo,
                    sewerageApplicationDetails.getWorkOrderDate(), SewerageTaxConstants.NOTICE_TYPE_WORK_ORDER_NOTICE);

            final String fileName = workOrederNo + ".pdf";
            final FileStoreMapper fileStore = fileStoreService.store(fileStream, fileName, "application/pdf",
                    SewerageTaxConstants.FILESTORE_MODULECODE);
            sewerageNotice.setFileStore(fileStore);
        }
        return sewerageNotice;
    }

    public SewerageNotice generateReportForEstimation(final SewerageApplicationDetails sewerageApplicationDetails,
            final HttpSession session) {
        SewerageNotice sewerageNotice = null;
        reportOutput = generateReportOutputDataForEstimation(sewerageApplicationDetails, session);
        if (reportOutput != null && reportOutput.getReportOutputData() != null) {
            generateNoticePDF = new ByteArrayInputStream(reportOutput.getReportOutputData());
            sewerageNotice = saveEstimationNotice(sewerageApplicationDetails, generateNoticePDF);
        }
        return sewerageNotice;
    }

    public SewerageNotice generateReportForWorkOrder(final SewerageApplicationDetails sewerageApplicationDetails,
            final HttpSession session) {
        SewerageNotice sewerageNotice = null;
        reportOutput = generateReportOutputForWorkOrder(sewerageApplicationDetails, session);
        if (reportOutput != null && reportOutput.getReportOutputData() != null) {
            generateNoticePDF = new ByteArrayInputStream(reportOutput.getReportOutputData());
            sewerageNotice = saveWorkOrderNotice(sewerageApplicationDetails, generateNoticePDF);
        }
        return sewerageNotice;
    }

    public ReportOutput generateReportOutputDataForEstimation(
            final SewerageApplicationDetails sewerageApplicationDetails, final HttpSession session) {
        reportParams = new HashMap<String, Object>(); 
        if (sewerageApplicationDetails != null) {
            final SimpleDateFormat formatter = new SimpleDateFormat("dd/MM/yyyy");
            final AssessmentDetails assessmentDetails = sewerageTaxUtils.getAssessmentDetailsForFlag(
                    sewerageApplicationDetails.getConnectionDetail().getPropertyIdentifier(),
                    PropertyExternalService.FLAG_FULL_DETAILS);
            String doorNo[] = null;
            if (null != assessmentDetails.getPropertyAddress())
                doorNo = assessmentDetails.getPropertyAddress().split(",");
            String ownerName = "";
            if (null != assessmentDetails.getOwnerNames())
                for (final OwnerName names : assessmentDetails.getOwnerNames()) {
                    ownerName = names.getOwnerName();
                    break;
                }

            if (SewerageTaxConstants.NEWSEWERAGECONNECTION.equalsIgnoreCase(sewerageApplicationDetails
                    .getApplicationType().getCode()))
                reportParams.put("applicationType",
                        WordUtils.capitalize(sewerageApplicationDetails.getApplicationType().getName()).toString());
            else
                reportParams.put("applicationType",
                        WordUtils.capitalize(sewerageApplicationDetails.getApplicationType().getName()).toString());
            reportParams.put("cityName", session.getAttribute("citymunicipalityname"));
            reportParams.put("district", session.getAttribute("districtName"));
            reportParams.put("estimationDate", formatter.format(sewerageApplicationDetails.getApplicationDate()));
            if (sewerageApplicationDetails.getCurrentDemand() != null) {
                for (EgDemandDetails egDmdDetails : sewerageApplicationDetails.getCurrentDemand().getEgDemandDetails()) {
                    if (egDmdDetails.getEgDemandReason().getEgDemandReasonMaster().getCode()
                            .equalsIgnoreCase(SewerageTaxConstants.FEES_DONATIONCHARGE_CODE)) {
                        donationCharges = egDmdDetails.getAmount().subtract(egDmdDetails.getAmtCollected());
                    }
                }
            }
            // TODO: CHECK THIS LOGIC AGAIN. IF FEE TYPE IS ESTIMATION FEES,
            // THEN WE NEED TO GROUP ALL FEESES.
            for (final SewerageConnectionFee scf : sewerageApplicationDetails.getConnectionFees()) {
                if (scf.getFeesDetail().getCode().equalsIgnoreCase(SewerageTaxConstants.FEES_ESTIMATIONCHARGES_CODE))
                    estimationCharges = BigDecimal.valueOf(scf.getAmount());
            } 
            reportParams.put("estimationCharges", estimationCharges);
            reportParams.put("donationCharges", donationCharges);
            reportParams.put("totalCharges", estimationCharges.add(donationCharges));
            reportParams.put("applicationDate", formatter.format(sewerageApplicationDetails.getApplicationDate()));
            reportParams.put("applicantName", ownerName);
            reportParams.put("address", assessmentDetails.getPropertyAddress());
            reportParams.put("inspectionDetails", sewerageApplicationDetails.getFieldInspections().get(0)
                    .getFieldInspectionDetails());
            reportParams.put("houseNo", doorNo != null ? doorNo[0] : "");
            reportInput = new ReportRequest(ESTIMATION_NOTICE, sewerageApplicationDetails.getEstimationDetails(),
                    reportParams);
        }
        return reportService.createReport(reportInput);
    }

    public ReportOutput generateReportOutputForWorkOrder(final SewerageApplicationDetails sewerageApplicationDetails,
            final HttpSession session) {
        reportParams = new HashMap<String, Object>(); 
        if (null != sewerageApplicationDetails) {
            final AssessmentDetails assessmentDetails = sewerageTaxUtils.getAssessmentDetailsForFlag(
                    sewerageApplicationDetails.getConnectionDetail().getPropertyIdentifier(),
                    PropertyExternalService.FLAG_FULL_DETAILS);
            final SimpleDateFormat formatter = new SimpleDateFormat("dd/MM/yyyy");
            final String doorno[] = assessmentDetails.getPropertyAddress().split(",");
            String ownerName = "";
            for (final OwnerName names : assessmentDetails.getOwnerNames()) {
                ownerName = names.getOwnerName();
                break;
            }

            if (SewerageTaxConstants.NEWSEWERAGECONNECTION.equalsIgnoreCase(sewerageApplicationDetails
                    .getApplicationType().getCode()))
                reportParams.put("conntitle",
                        WordUtils.capitalize(sewerageApplicationDetails.getApplicationType().getName()).toString());
            else
                reportParams.put("conntitle",
                        WordUtils.capitalize(sewerageApplicationDetails.getApplicationType().getName()).toString());
            reportParams.put("applicationtype", stmsMessageSource.getMessage("msg.new.sewerage.conn", null, null));
            reportParams.put("municipality", session.getAttribute("citymunicipalityname"));
            reportParams.put("district", session.getAttribute("districtName"));
            reportParams.put("purpose", null);

            reportParams.put(
                    "presentCommissioner",
                    assignmentService
                            .getAllActiveAssignments(
                                    designationService.getDesignationByName(
                                            SewerageTaxConstants.DESIGNATION_COMMISSIONER).getId()).get(0)
                            .getEmployee().getName());
            
            if(sewerageApplicationDetails.getApplicationType().getCode().equalsIgnoreCase(SewerageTaxConstants.NEWSEWERAGECONNECTION)){
                for (final SewerageConnectionFee scf : sewerageApplicationDetails.getConnectionFees()) {
                    if (scf.getFeesDetail().getCode().equalsIgnoreCase(SewerageTaxConstants.FEES_ESTIMATIONCHARGES_CODE))
                        estimationCharges = BigDecimal.valueOf(scf.getAmount());
                    else if(scf.getFeesDetail().getCode().equalsIgnoreCase(SewerageTaxConstants.FEES_DONATIONCHARGE_CODE))
                        donationCharges = BigDecimal.valueOf(scf.getAmount());
                    else if(scf.getFeesDetail().getCode().equalsIgnoreCase(SewerageTaxConstants.FEES_SEWERAGETAX_CODE))
                        sewerageCharges = BigDecimal.valueOf(scf.getAmount());
                }
            } else{ 
                if (sewerageApplicationDetails.getCurrentDemand() != null) {
                     Map<String, BigDecimal> donationSewerageFeesDtls = getFeesForChangeInClosets(sewerageApplicationDetails.getCurrentDemand());
                    estimationCharges = donationSewerageFeesDtls.get("estimationCharges");
                    donationCharges = donationSewerageFeesDtls.get("donationCharges");
                    sewerageCharges = donationSewerageFeesDtls.get("sewerageTax");
                } 
            }
            reportParams.put("estimationCharges", estimationCharges);
            reportParams.put("donationCharges", donationCharges);
            reportParams.put("sewerageTax", sewerageCharges); 
            reportParams.put("totalCharges", donationCharges.add(sewerageCharges).add(estimationCharges));

            reportParams.put("assessmentNo", sewerageApplicationDetails.getConnectionDetail().getPropertyIdentifier());
            reportParams.put("noOfSeatsResidential", sewerageApplicationDetails.getConnectionDetail()
                    .getNoOfClosetsResidential());
            reportParams.put("noOfSeatsNonResidential", sewerageApplicationDetails.getConnectionDetail()
                    .getNoOfClosetsNonResidential());
            reportParams.put("revenueWardNo", assessmentDetails.getBoundaryDetails().getWardName());
            reportParams.put("locality", assessmentDetails.getBoundaryDetails().getLocalityName());

            reportParams.put("workorderdate", sewerageApplicationDetails.getWorkOrderDate() != null?formatter.format(sewerageApplicationDetails.getWorkOrderDate()):"");
            reportParams.put("workorderno", sewerageApplicationDetails.getWorkOrderNumber());
            if (sewerageApplicationDetails.getConnection().getShscNumber() != null)
                reportParams.put("consumerNumber", sewerageApplicationDetails.getConnection().getShscNumber());
            reportParams.put("applicantname", WordUtils.capitalize(ownerName)); 
            reportParams.put("address", assessmentDetails.getPropertyAddress());
            reportParams.put("doorno", doorno[0]);
            reportParams.put("applicationDate", formatter.format(sewerageApplicationDetails.getApplicationDate()));
            reportInput = new ReportRequest(WORKORDERNOTICE, sewerageApplicationDetails, reportParams);
        }
        return reportService.createReport(reportInput);
    }
    
    public Map<String, BigDecimal> getFeesForChangeInClosets(final EgDemand demand) {
        BigDecimal currentEstimationCharges = BigDecimal.ZERO;

        BigDecimal totalDontationCharge = BigDecimal.ZERO;
        BigDecimal totalSewerageTax = BigDecimal.ZERO;

        Map<String, BigDecimal> donationSewerageFees = new HashMap<String, BigDecimal>();
        
        for (final EgDemandDetails dmdDtl : demand.getEgDemandDetails()) {
            
            for(EgdmCollectedReceipt collectedReceipt : dmdDtl.getEgdmCollectedReceipts()){
                if (SewerageTaxConstants.FEES_DONATIONCHARGE_CODE
                        .equalsIgnoreCase(dmdDtl.getEgDemandReason().getEgDemandReasonMaster().getCode())) {
                    totalDontationCharge=totalDontationCharge.add(collectedReceipt.getReasonAmount());
                }
                else if (SewerageTaxConstants.FEES_SEWERAGETAX_CODE
                        .equalsIgnoreCase(dmdDtl.getEgDemandReason().getEgDemandReasonMaster().getCode())) {
                    totalSewerageTax=totalSewerageTax.add(collectedReceipt.getReasonAmount());
                }else  if (SewerageTaxConstants.FEES_ESTIMATIONCHARGES_CODE 
                        .equalsIgnoreCase(dmdDtl.getEgDemandReason().getEgDemandReasonMaster().getCode())) {
                    currentEstimationCharges=currentEstimationCharges.add(collectedReceipt.getReasonAmount());
                }
            }
        }
        donationSewerageFees.put("donationCharges", totalDontationCharge);
        donationSewerageFees.put("sewerageTax", totalSewerageTax);
        donationSewerageFees.put("estimationCharges", currentEstimationCharges);
        return donationSewerageFees;
    }
 

    /**
     * @param outputStream
     * @param inputPdfList
     * @param out
     * @return Merge pdf files
     */
    public byte[] mergePdfFiles(List<InputStream> inputPdfList, OutputStream outputStream) throws Exception {
        // Create document and pdfReader objects.
        Document document = new Document();
        List<PdfReader> readers = new ArrayList<PdfReader>();
        int totalPages = 0;
        // Create pdf Iterator object using inputPdfList.
        Iterator<InputStream> pdfIterator = inputPdfList.iterator();
        // Create reader list for the input pdf files.
        while (pdfIterator.hasNext()) {
            InputStream pdf = pdfIterator.next();
            if (pdf != null) {
                PdfReader pdfReader = new PdfReader(pdf);
                readers.add(pdfReader);
                totalPages = totalPages + pdfReader.getNumberOfPages();
            } else {
                break;
            }
        }
        // Create writer for the outputStream
        PdfWriter writer = PdfWriter.getInstance(document, outputStream);
        // Open document.
        document.open();
        // Contain the pdf data.
        PdfContentByte pageContentByte = writer.getDirectContent();
        PdfImportedPage pdfImportedPage;
        int currentPdfReaderPage = 1;
        Iterator<PdfReader> iteratorPDFReader = readers.iterator();
        // Iterate and process the reader list.
        while (iteratorPDFReader.hasNext()) {
            PdfReader pdfReader = iteratorPDFReader.next();
            // Create page and add content.
            while (currentPdfReaderPage <= pdfReader.getNumberOfPages()) {
                document.newPage();
                pdfImportedPage = writer.getImportedPage(pdfReader, currentPdfReaderPage);
                pageContentByte.addTemplate(pdfImportedPage, 0, 0);
                currentPdfReaderPage++;
            }
            currentPdfReaderPage = 1;
        }
        // Close document and outputStream.
        outputStream.flush();
        document.close();
        outputStream.close();
        return ((ByteArrayOutputStream) outputStream).toByteArray();
    }

    /**
     * @param inputStream
     * @param noticeNo
     * @param out
     * @return zip output stream file
     */
    public ZipOutputStream addFilesToZip(final InputStream inputStream, final String noticeNo, final ZipOutputStream out) {
        if (LOGGER.isDebugEnabled())
            LOGGER.debug("Entered into addFilesToZip method");
        final byte[] buffer = new byte[1024];
        try {
            out.setLevel(Deflater.DEFAULT_COMPRESSION);
            out.putNextEntry(new ZipEntry(noticeNo.replaceAll("/", "_")));
            int len;
            while ((len = inputStream.read(buffer)) > 0)
                out.write(buffer, 0, len);
            inputStream.close();

        } catch (final IllegalArgumentException iae) {
            LOGGER.error("Exception in addFilesToZip : ", iae);
            iae.printStackTrace();
            throw new ValidationException(Arrays.asList(new ValidationError("error", iae.getMessage())));
        } catch (final FileNotFoundException fnfe) {
            LOGGER.error("Exception in addFilesToZip : ", fnfe);
            fnfe.printStackTrace();
            throw new ValidationException(Arrays.asList(new ValidationError("error", fnfe.getMessage())));
        } catch (final IOException ioe) {
            LOGGER.error("Exception in addFilesToZip : ", ioe);
            ioe.printStackTrace();
            throw new ValidationException(Arrays.asList(new ValidationError("error", ioe.getMessage())));
        }
        if (LOGGER.isDebugEnabled())
            LOGGER.debug("Exit from addFilesToZip method");
        return out;
    }
    
    
    public SewerageNotice generateReportForCloseConnection(final SewerageApplicationDetails sewerageApplicationDetails,
            final HttpSession session) {
        SewerageNotice sewerageNotice = null;
        reportOutput = generateReportOutputForSewerageCloseConnection(sewerageApplicationDetails, session);
        if (reportOutput != null && reportOutput.getReportOutputData() != null) {
            generateNoticePDF = new ByteArrayInputStream(reportOutput.getReportOutputData());
            sewerageNotice = saveCloseConnectionNotice(sewerageApplicationDetails, generateNoticePDF);
        }
        return sewerageNotice;
    }
    
    public ReportOutput generateReportOutputForSewerageCloseConnection(final SewerageApplicationDetails sewerageApplicationDetails,
            final HttpSession session) {
        reportParams = new HashMap<String, Object>(); 
        if (null != sewerageApplicationDetails) {
            final AssessmentDetails assessmentDetails = sewerageTaxUtils.getAssessmentDetailsForFlag(
                    sewerageApplicationDetails.getConnectionDetail().getPropertyIdentifier(),
                    PropertyExternalService.FLAG_FULL_DETAILS);
            final SimpleDateFormat formatter = new SimpleDateFormat("dd/MM/yyyy");
            final String doorno[] = assessmentDetails.getPropertyAddress().split(",");
            String ownerName = "";
            for (final OwnerName names : assessmentDetails.getOwnerNames()) {
                ownerName = names.getOwnerName();
                break;
            }
            reportParams.put("conntitle",
                        WordUtils.capitalize(sewerageApplicationDetails.getApplicationType().getName()).toString());
            reportParams.put("municipality", session.getAttribute("citymunicipalityname"));
            reportParams.put("district", session.getAttribute("districtName"));

            reportParams.put(
                    "presentCommissioner",
                    assignmentService
                            .getAllActiveAssignments(
                                    designationService.getDesignationByName(
                                            SewerageTaxConstants.DESIGNATION_COMMISSIONER).getId()).get(0)
                            .getEmployee().getName());
            
            reportParams.put("assessmentNo", sewerageApplicationDetails.getConnectionDetail().getPropertyIdentifier());
            reportParams.put("noOfSeatsResidential", sewerageApplicationDetails.getConnectionDetail()
                    .getNoOfClosetsResidential());
            reportParams.put("noOfSeatsNonResidential", sewerageApplicationDetails.getConnectionDetail()
                    .getNoOfClosetsNonResidential());
            reportParams.put("revenueWardNo", assessmentDetails.getBoundaryDetails().getWardName());
            reportParams.put("locality", assessmentDetails.getBoundaryDetails().getLocalityName());

            reportParams.put("eeApprovalDate", formatter.format(sewerageApplicationDetails.getLastModifiedDate()));
            reportParams.put("consumerNumber", sewerageApplicationDetails.getConnection().getShscNumber());
            reportParams.put("applicantname", WordUtils.capitalize(ownerName)); 
            reportParams.put("address", assessmentDetails.getPropertyAddress());
            reportParams.put("doorno", doorno[0]);
            reportParams.put("applicationDate", formatter.format(sewerageApplicationDetails.getApplicationDate()));
            reportInput = new ReportRequest(CLOSECONNECTIONNOTICE, sewerageApplicationDetails, reportParams);
        }
        return reportService.createReport(reportInput);
    }
    
    public SewerageNotice saveCloseConnectionNotice(final SewerageApplicationDetails sewerageApplicationDetails,
            final InputStream fileStream) {
        SewerageNotice sewerageNotice = null;

        if (sewerageApplicationDetails != null) {
            sewerageNotice = new SewerageNotice();
            buildSewerageNotice(sewerageApplicationDetails, sewerageNotice, sewerageApplicationDetails.getClosureNoticeNumber(),
                    new Date(), SewerageTaxConstants.NOTICE_TYPE_CLOSER_NOTICE);
            final String fileName = sewerageApplicationDetails.getClosureNoticeNumber() + ".pdf";
            final FileStoreMapper fileStore = fileStoreService.store(fileStream, fileName, "application/pdf",
                    SewerageTaxConstants.FILESTORE_MODULECODE);
            sewerageNotice.setFileStore(fileStore);
        }
        return sewerageNotice;
    }
    
    public ReportOutput getSewerageCloseConnectionNotice(final SewerageApplicationDetails sewerageApplicationDetails, 
            final File file, final HttpSession session,final HttpServletResponse response) throws IOException{
        Map<String, Object> reportParams = new HashMap<String, Object>(); 
        ReportRequest reportInput = null;
        OutputStream os;
        InputStream is = new FileInputStream(file);
        os = response.getOutputStream();
        reportParams.put("closenoticereport",os);
        byte[] buffer = new byte[1024];
        int len;
        while ((len = is.read(buffer)) != -1) {
            os.write(buffer, 0, len);
        }
        os.flush();
        os.close();
        is.close();
        
        reportInput = new ReportRequest(sewerageApplicationDetails.getClosureNoticeNumber(), sewerageApplicationDetails, reportParams);
        return reportService.createReport(reportInput);
    }

}
