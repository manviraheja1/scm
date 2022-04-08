import React, { useState } from "react"
import { EditPencilIcon, LogoutIcon } from "@egovernments/digit-ui-react-components";
import TopBar from "./TopBar";
import { useHistory } from "react-router-dom";
import SideBar from "./SideBar";
import Dialog from "./SideBar/ConfirmationDialog";

const TopBarSideBar = ({ t, stateInfo, userDetails, CITIZEN, cityDetails, mobileView, handleUserDropdownSelection, logoUrl, showSidebar = true ,showLanguageChange}) => {
    const [isSidebarOpen, toggleSidebar] = useState(false);
    const [showDialog,setShowDialog] = useState(false);
    const history = useHistory();

    const handleLogout = () => {
      toggleSidebar(false);
      Digit.UserService.logout();
    };
    const userProfile=()=>{
      history.push("/digit-ui/employee/user/profile");
    }
  
    const Logout = ()=>{
      setShowDialog(true);
    }

    const userOptions = [{ name: t("EDIT_PROFILE"), icon: <EditPencilIcon className="icon" />, func: userProfile },{ name: t("CORE_COMMON_LOGOUT"), icon: <LogoutIcon className="icon" />, func: Logout }];
    if(showDialog){
      return <Dialog onSelect={()=> setShowDialog(false)} onCancel={()=>setShowDialog(false)}/>;
    }
    return (
      <React.Fragment>
        <TopBar
          t={t}
          stateInfo={stateInfo}
          toggleSidebar={toggleSidebar}
          isSidebarOpen={isSidebarOpen}
          handleLogout={handleLogout}
          userDetails={userDetails}
          CITIZEN={CITIZEN}
          cityDetails={cityDetails}
          mobileView={mobileView}
          userOptions={userOptions}
          handleUserDropdownSelection={handleUserDropdownSelection}
          logoUrl={logoUrl}
          showLanguageChange={showLanguageChange}
        />
        {showSidebar && <SideBar
          t={t}
          CITIZEN={CITIZEN}
          isSidebarOpen={isSidebarOpen}
          toggleSidebar={toggleSidebar}
          handleLogout={handleLogout}
          mobileView={mobileView}
          userDetails={userDetails}
        />
        }
      </React.Fragment>
    );
  }

  export default TopBarSideBar