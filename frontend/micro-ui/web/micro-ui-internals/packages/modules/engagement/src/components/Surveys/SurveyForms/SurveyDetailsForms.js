import { CardLabelError, Dropdown, RemoveableTag, TextInput } from "@egovernments/digit-ui-react-components";
import React, {Fragment, useMemo } from "react";
import { Controller } from "react-hook-form";

import { alphabeticalSortFunctionForTenantsBasedOnName } from "../../../utils/index";
const SurveyDetailsForms = ({ t, registerRef, controlSurveyForm, surveyFormState, surveyFormData, disableInputs }) => {
  const ulbs = Digit.SessionStorage.get("ENGAGEMENT_TENANTS");
  const tenantId = Digit.ULBService.getCurrentTenantId();
  const userInfo = Digit.UserService.getUser().info;
  const userUlbs = ulbs
    .filter((ulb) => userInfo?.roles?.some((role) => role?.tenantId === ulb?.code))
    .sort(alphabeticalSortFunctionForTenantsBasedOnName);
  const selectedTenat = useMemo(() => {
    const filtered = ulbs.filter((item) => item.code === tenantId);
    return filtered;
  }, [ulbs]);

  return (
    <div className="surveydetailsform-wrapper">
      <span className="surveyformfield">
      <label>{`${t("LABEL_FOR_ULB")} * :`}</label>
        <Controller
          name="tenantIds"
          control={controlSurveyForm}
          defaultValue={selectedTenat}
          render={(props) => {
            const renderRemovableTokens = useMemo(
              () =>
                props?.value?.map((ulb, index) => {
                  return (
                    <RemoveableTag
                      key={index}
                      text={ulb.name}
                      onClick={() => {
                        props.onChange(props?.value?.filter((loc) => loc.code !== ulb.code));
                      }}
                    />
                  );
                }),
              [props?.value]
            );
            return (
              <div style={{display:"grid", gridAutoFlow:"row"}}>
                <Dropdown
                  allowMultiselect={true}
                  optionKey={"i18nKey"}
                  option={userUlbs}
                  select={(e) => {
                    props.onChange([...(surveyFormData("tenantIds")?.filter?.((f) => e.code !== f?.code) || []), e]);
                  }}
                  selected={props?.value}
                  keepNull={true}
                  disable={disableInputs}
                  t={t}
                />
                <div className="tag-container">{renderRemovableTokens}</div>
              </div>
            );
          }}
        />
      </span>

      <span className="surveyformfield">
        <label>{t("CS_SURVEY_NAME")}</label>
        <TextInput
          name="title"
          type="text"
          inputRef={registerRef({
            maxLength: {
              value: 60,
              message: t("EXCEEDS_60_CHAR_LIMIT"),
            },
          })}
          disable={disableInputs}
        />
        {surveyFormState?.errors?.title && <CardLabelError>{surveyFormState?.errors?.["title"]?.message}</CardLabelError>}
      </span>
      <span className="surveyformfield">
        <label>{t("CS_SURVEY_DESCRIPTION")}</label>
        <TextInput
          name="description"
          type="text"
          inputRef={registerRef({
            maxLength: {
              value: 250,
              message: t("EXCEEDS_250_CHAR_LIMIT"),
            },
          })}
          disable={disableInputs}
        />
        {surveyFormState?.errors?.description && <CardLabelError>{surveyFormState?.errors?.["description"]?.message}</CardLabelError>}
      </span>
    </div>
  );
};

export default SurveyDetailsForms;
