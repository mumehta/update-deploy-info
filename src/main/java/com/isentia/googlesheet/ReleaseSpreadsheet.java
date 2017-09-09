package com.isentia.googlesheet;

/**
 * Created by MMehta on 6/06/2017.
 */

import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.UpdateValuesResponse;
import com.google.api.services.sheets.v4.model.ValueRange;

import java.io.IOException;
import java.util.*;

import static com.isentia.googlesheet.GoogleSheetUtils.getSheetsService;

public class ReleaseSpreadsheet {
    /**
     * Enum to store Sheet-Names in Release Sheet
     */
    private enum Sheet {DaaS_Release, MediaPortal, MP2Cloud}

    /**
     * Enum to store environments in Release Sheet
     */
    private enum Environment { Dev, Uat, Production }
    private static final Map<Environment, String> envColumns = Collections.unmodifiableMap(initializeMapping());

    private static Map<Environment, String> initializeMapping() {
        Map<Environment, String> envColumns = new HashMap<>(3);
        envColumns.put(Environment.Dev, "B");
        envColumns.put(Environment.Uat, "C");
        envColumns.put(Environment.Production, "D");
        return envColumns;
    }

    /**
     * No args constructor
     */
    public ReleaseSpreadsheet() {
        super();
    }

    /**
     * Returns the Sheet name based on project being used.
     *
     * @param
     *      project: which project
     * @return
     *      Sheet: Sheet Name
     */
    private Sheet getSheet(String project) {
        Sheet sheet;
        switch (project.toLowerCase()) {
            case "daas":
                sheet = Sheet.DaaS_Release;
                break;
            case "mp":
                sheet = Sheet.MediaPortal;
                break;
            case "mp2cloud":
                sheet = Sheet.MP2Cloud;
                break;
            default:
                throw new IllegalArgumentException("Invalid project: " + project);
        }

        return sheet;
    }

    private String getColumnIndex(String environment){
        Environment env;
        switch (environment) {
            case "dev":
                env = Environment.Dev;
                break;
            case "uat":
                env = Environment.Uat;
                break;
            case "prod":
                env = Environment.Production;
                break;
            default:
                throw new IllegalArgumentException("Invalid environment: " + environment);
        }
        return envColumns.get(env);
    }

    private String getRowIndex(String clientJsonString, String sheetName, String artifact){
        Sheets service;
        int rowIndex = 0;
        String spreadSheetId = "1AVHYdqnsyYy84irPTIMoWbPHF46INNlQWOdm56Lp4VE";
        String range = sheetName + "!A1:A50";
        try {
            service = getSheetsService(clientJsonString);
            ValueRange response = service.spreadsheets().values()
                    .get(spreadSheetId, range).setMajorDimension("COLUMNS").execute();

            List<List<Object>> values = response.getValues();
            List<Object> artifacts = values.get(0);
            if (values == null || values.size() == 0) {
                System.out.println("No data found.");
            } else {
                rowIndex = artifacts.indexOf(artifact);
                if(rowIndex == -1){
                    System.out.println("The artifact: " + artifact + " entry not found in given spreadsheet.");
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return Integer.toString(rowIndex+1);
    }

    private String calculateRange(String clientJsonString, String spreadSheetName, String environment, String artifact){
        String rowIndex = getRowIndex(clientJsonString, spreadSheetName, artifact);
        String colIndex = getColumnIndex(environment);
        return spreadSheetName + "!" + colIndex + rowIndex;
    }

    private UpdateValuesResponse saveReleaseInfo(String clientJsonPath, String cellRange, String semanticVersion) {
        String spreadsheetId = "1AVHYdqnsyYy84irPTIMoWbPHF46INNlQWOdm56Lp4VE";
        String range = cellRange;
        String valueInputOption = "RAW";

        List<List<Object>> writeData = new ArrayList<>();
        List<Object> dataRow = new ArrayList<>();
        dataRow.add(semanticVersion);
        writeData.add(dataRow);

        ValueRange requestBody = new ValueRange().setValues(writeData);

        UpdateValuesResponse response = null;
        try {
            Sheets service = getSheetsService(clientJsonPath);
            Sheets.Spreadsheets.Values.Update request =
                    service.spreadsheets().values().update(spreadsheetId, range, requestBody);
            request.setValueInputOption(valueInputOption);

            response = request.execute();
            System.out.println(response);

        } catch (IOException e) {
            e.printStackTrace();
        }



        return response;
    }

    public UpdateValuesResponse updateReleaseSheet(String clientJsonPath, String project, String artifact, String environment, String semanticVersion){
        String sheetName = getSheet(project).toString();
        String cellRange = calculateRange(clientJsonPath, sheetName, environment, artifact);
        UpdateValuesResponse response = saveReleaseInfo(clientJsonPath, cellRange, semanticVersion);
        return response;
    }

    /*public static void main(String[] args) throws IOException {
        ReleaseSpreadsheet rs = new ReleaseSpreadsheet();
        rs.updateReleaseSheet("MP", "mp-api-node", "dev", "1.0.11");
    }*/
}
