import { Header, TextInput, ArrowForward, Loader, BackButton} from "@egovernments/digit-ui-react-components";
import React, { useState, Fragment, useRef, useEffect}from "react";
import { useTranslation } from "react-i18next";
import FAQComponent from "./FAQComponent";


const FAQsSection = () => {
  const user = Digit.UserService.getUser();
  const tenantId = user?.info?.tenantId || Digit.ULBService.getCurrentTenantId();
  const { t } = useTranslation();

  
   const { isLoading, data } = Digit.Hooks.useGetDSSFAQsJSON(Digit.ULBService.getStateId());

   const moduleFAQs = data?.MdmsRes["dss-dashboard"]?.FAQs[0]?.[`DSS`].FAQs;

   if(isLoading){
   return <Loader/>
   }
  return (
    <Fragment>
    <div className="faq-page">
      <BackButton></BackButton>
        <div style={{ marginBottom: "15px" }}>
          <Header styles={{ marginLeft: "0px", paddingTop: "10px", fontSize: "36px"}}>{t("DSS_FAQS")}</Header>
        </div>

        <div className="faq-list">
        {moduleFAQs.map((faq, i) => (
          <FAQComponent key={"FAQ" + i} question={faq.question} answer={faq.answer} index={i+1}/>
        ))}
        </div>
    </div>
    </Fragment>
  );
};

export default FAQsSection;