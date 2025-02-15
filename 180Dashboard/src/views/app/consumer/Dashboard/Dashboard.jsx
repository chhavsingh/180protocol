import React, {useEffect, useRef, useState} from "react";
import Grid from "../../../../components/Grid";
import {Field, Form, Formik} from "formik";
import {FormikReactSelect} from "../../../../containers/FormikFields";
import {
    createAggregationRequest,
    fetchDecryptedDataOutput,
    fetchEncryptedDataOutput
} from "../../../../store/consumer/actions";
import {useAuthDispatch} from "../../../../store/context";
import {ucWords} from "../../../../utils/helpers";
import moment from "moment";
import AlertBox from "../../../../components/AlertBox";

// Styles
import styles from './Dashboard.module.scss';

// Images
import downloadIcon from "../../../../assets/images/download.svg";
import selectDownArrow from "../../../../assets/images/select-down-arrow.svg";
import exportIcon from "../../../../assets/images/export.svg";
import refreshIcon from "../../../../assets/images/refresh.svg";

const RightArrowIcon = () => {
    return (
        <div className={styles.IndicatorArrowDiv}>
            <img src={selectDownArrow} className={styles.customSelectArrow} width={20}/>
        </div>
    )
}

const Dashboard = (props) => {
    const dispatch = useAuthDispatch();

    const [columns, setColumns] = useState([]);

    const [encryptedDataOutput, setEncryptedDataOutput] = useState([]);

    const [rows, setRows] = useState([]);
    const [lastRequestDate, setLastRequestDate] = useState(null);
    const alertRef = useRef();

    const dataTypeOptions = localStorage.getItem('dataTypeOptions') ? JSON.parse(localStorage.getItem('dataTypeOptions')) : [];

    useEffect(() => {
        async function fetchData() {
            return await fetchEncryptedDataOutput(dispatch, props.apiUrl, {});
        }

        fetchData().then((response) => {
            getDecryptedData(response);
        });
    }, [dispatch]);

    const getDecryptedData = (response) => {
        setEncryptedDataOutput(response);
        if (response.states && response.states.length > 0) {
            let sortedDataOutput = response.states.sort(function (a, b) {
                return new Date(b.state.data.dateCreated) - new Date(a.state.data.dateCreated)
            })
            setLastRequestDate(moment.utc(sortedDataOutput[0].state.data.dateCreated).format("MMM DD, YYYY hh:mm:ss A"));

            getDecryptedDataOutput(response.states[0].state.data.flowTopic)
        }
    }

    const save = async (values, {resetForm}) => {
        let params = {
            "options": {
                "trackProgress": true
            },
            "dataType": values.dataType.value,
            "description": values.description
        };

        let response = await createAggregationRequest(dispatch, props.apiUrl, params);
        if (response) {
            resetForm({values: ''})
            alertRef.current.showAlert('success', 'Request submitted successfully.')
            let res = await fetchEncryptedDataOutput(dispatch, props.apiUrl, {});
            getDecryptedData(res);
        }
    }

    const refresh = async () => {
        let response = await fetchEncryptedDataOutput(dispatch, props.apiUrl, {})
        getDecryptedData(response);
    }

    const validate = (values) => {
        let errors = {};

        if (!values.dataType) {
            errors.dataType = "Please select one of data category";
        }

        if (!values.description) {
            errors.description = "Please enter description";
        }

        return errors;
    }

    const getDecryptedDataOutput = async (flowTopic) => {
        let params = {
            "options": {
                "trackProgress": true
            },
            "flowId": flowTopic
        }

        let decryptedDataOutput = await fetchDecryptedDataOutput(dispatch, props.apiUrl, params);
        let columns = [];
        for (let i = 0; i < decryptedDataOutput.length; i++) {
            for (let property in decryptedDataOutput[i]) {
                if (columns.length < Object.keys(decryptedDataOutput[i]).length) {
                    columns.push({name: property, title: ucWords(property)});
                }
            }
        }

        setColumns(columns);
        setRows(decryptedDataOutput);
    }

    const exportAsCSV = () => {
        let csvData = [];
        csvData.push(Object.keys(rows[0]))
        for (let i = 0; i < rows.length; i++) {
            csvData.push(Object.values(rows[i]));
        }

        let csvContent = "data:text/csv;charset=utf-8,"
            + csvData.map(e => e.join(",")).join("\n");

        let encodedUri = encodeURI(csvContent);
        let link = document.createElement("a");
        link.setAttribute("href", encodedUri);
        link.setAttribute("download", "my_data.csv");
        document.body.appendChild(link);

        link.click();
    }

    return (
        <>
            <section className={`${styles.accessData}`}>
                <div className={styles.bgGradient}>
                    <div className={`container ${styles.OverviewContainer}`}>
                        <div className="row">
                            <div className="col-sm-12 col-md-6">
                                <div className="innerCol">
                                    <p>Total Requests</p>
                                    <p className='bigText mb-0'>{encryptedDataOutput && encryptedDataOutput.states && encryptedDataOutput.states.length || 0}</p>
                                </div>
                            </div>
                            <div className="col-sm-12 col-md-6">
                                <div className="innerCol">
                                    <p>Last Request</p>
                                    <p className='bigText mb-0'>{lastRequestDate || '-'}</p>
                                </div>
                            </div>
                        </div>
                    </div>
                    <div className="outerSpace"></div>
                </div>
                <div className="mainContentSection">
                    <div className={`container mb-5 ${styles.accessDataContainer}`}>
                        <div className="card">
                            <div className="card-header">
                                <h3>Access Data</h3>
                            </div>
                            <div className={`card-body ${styles.accessDataCardBody}`}>
                                <div className={styles.accessDataBodyInner}>
                                    <div className="row">
                                        <Formik
                                            validate={validate}
                                            initialValues={{
                                                dataType: "",
                                                description: "",
                                            }}
                                            onSubmit={save}
                                        >
                                            {({errors, touched, values, setFieldValue, setFieldTouched}) => (
                                                <Form className='auth-form'>
                                                    <div className="col-sm-12 col-md-12">
                                                        <div className={styles.accessDataBoxInner}>
                                                            <div className={styles.selectCateBox}>
                                                                <p>Data Category</p>
                                                                <FormikReactSelect
                                                                    className={styles.customSelect}
                                                                    name="dataType"
                                                                    id="dataType"
                                                                    value={values.dataType}
                                                                    isMulti={false}
                                                                    options={dataTypeOptions}
                                                                    onChange={setFieldValue}
                                                                    onBlur={setFieldTouched}
                                                                    components={{
                                                                        DropdownIndicator: RightArrowIcon,
                                                                        IndicatorSeparator: () => null
                                                                    }}
                                                                />
                                                                {errors.dataType && touched.dataType &&
                                                                <div
                                                                    className="invalid-feedback-msg">{errors.dataType}</div>}
                                                            </div>
                                                            <div className={styles.descriptionCateBox}>
                                                                <p>Description</p>
                                                                <div>
                                                                    <Field name="description"
                                                                           className={styles.inputFormControl}/>
                                                                    {errors.description && touched.description &&
                                                                    <div
                                                                        className="invalid-feedback-msg">{errors.description}</div>}
                                                                </div>
                                                            </div>
                                                        </div>
                                                    </div>
                                                    <div className={`col-sm-12 col-md-12`}>
                                                        <div className={styles.submitBoxInner}>
                                                            <div className={styles.submitBtnBox}>
                                                                <button type="submit" name="Submit">Submit</button>
                                                                <p>Completed requests will be shown in the table
                                                                    below</p>
                                                            </div>
                                                        </div>
                                                    </div>
                                                </Form>
                                            )}
                                        </Formik>
                                    </div>
                                </div>
                            </div>
                        </div>
                    </div>
                    <div className={`container mb-5 ${styles.requestsContainer}`}>
                        <div className="card">
                            <div className={`card-header ${styles.requestsCardHeader}`}>
                                <h3>Requests</h3>
                                <div className={styles.refreshContainer}>
                                    <p>Use refresh button to load latest results</p>
                                    <button type="button" name="Refresh"
                                            onClick={refresh}>REFRESH <img
                                        src={refreshIcon} alt="refresh"/>
                                    </button>
                                </div>
                            </div>
                            <div className={`card-body ${styles.requestsCardBody}`}>
                                <div className={styles.requestsBodyInner}>
                                    <div className={styles.requestsBoxInner}>
                                        <div className="row five-col">
                                            {
                                                encryptedDataOutput && encryptedDataOutput.states && encryptedDataOutput.states.length > 0 ?
                                                    encryptedDataOutput.states.map((output, index) => {
                                                        return (
                                                            <div key={index}
                                                                 onClick={() => getDecryptedDataOutput(output.state.data.flowTopic)}
                                                                 className="col-sm-12 col-md-3 col-lg-4 col-xl-2">
                                                                <div className={styles.downloadRequestBox}>
                                                                    <img src={downloadIcon} alt="download"/>
                                                                    <div className={styles.requestInfoBox}>
                                                                        <p>{output.state.data.dataType}</p>
                                                                        <p>{output.state.data.description}</p>
                                                                    </div>
                                                                </div>
                                                            </div>
                                                        );
                                                    }) : <p>No completed requests at this time.</p>
                                            }
                                        </div>
                                    </div>
                                </div>
                            </div>
                        </div>
                    </div>
                    {
                        rows && rows.length > 0 ?
                            <div className={`container mb-5 ${styles.exportDataContainer}`}>
                                <div className="card">
                                    <div className={`card-header ${styles.exportCardHeader}`}>
                                        <h3>Preview</h3>
                                        <button onClick={exportAsCSV}>EXPORT <img src={exportIcon}
                                                                                  className={styles.exportbtnIcon}
                                                                                  alt="export"/>
                                        </button>
                                    </div>
                                    <div className={`card-body ${styles.previewTableCardBody}`}>
                                        <div className={styles.previewTableBodyInner}>
                                            <Grid className={styles.aggregationsTable} columns={columns} rows={rows}/>
                                        </div>
                                    </div>
                                </div>
                            </div> : null
                    }
                </div>
            </section>
            <AlertBox ref={alertRef}/>
        </>
    )
}

export default Dashboard;
