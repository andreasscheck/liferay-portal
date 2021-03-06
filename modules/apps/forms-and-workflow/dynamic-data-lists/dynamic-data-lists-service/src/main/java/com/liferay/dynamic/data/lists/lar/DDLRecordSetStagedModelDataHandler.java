/**
 * Copyright (c) 2000-present Liferay, Inc. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 */

package com.liferay.dynamic.data.lists.lar;

import com.liferay.dynamic.data.lists.model.DDLRecordSet;
import com.liferay.dynamic.data.lists.model.DDLRecordSetConstants;
import com.liferay.dynamic.data.lists.model.DDLRecordSetSettings;
import com.liferay.dynamic.data.lists.service.DDLRecordSetLocalService;
import com.liferay.dynamic.data.mapping.io.DDMFormValuesJSONDeserializer;
import com.liferay.dynamic.data.mapping.model.DDMForm;
import com.liferay.dynamic.data.mapping.model.DDMStructure;
import com.liferay.dynamic.data.mapping.model.DDMTemplate;
import com.liferay.dynamic.data.mapping.storage.DDMFormValues;
import com.liferay.dynamic.data.mapping.util.DDMFormFactory;
import com.liferay.exportimport.kernel.lar.ExportImportPathUtil;
import com.liferay.exportimport.kernel.lar.PortletDataContext;
import com.liferay.exportimport.kernel.lar.StagedModelDataHandler;
import com.liferay.exportimport.kernel.lar.StagedModelDataHandlerUtil;
import com.liferay.exportimport.kernel.lar.StagedModelModifiedDateComparator;
import com.liferay.exportimport.lar.BaseStagedModelDataHandler;
import com.liferay.portal.kernel.dao.orm.QueryUtil;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.service.ServiceContext;
import com.liferay.portal.kernel.util.MapUtil;
import com.liferay.portal.kernel.xml.Element;

import java.util.List;
import java.util.Map;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * @author Brian Wing Shun Chan
 */
@Component(immediate = true, service = StagedModelDataHandler.class)
public class DDLRecordSetStagedModelDataHandler
	extends BaseStagedModelDataHandler<DDLRecordSet> {

	public static final String[] CLASS_NAMES = {DDLRecordSet.class.getName()};

	@Override
	public void deleteStagedModel(DDLRecordSet recordSet)
		throws PortalException {

		_ddlRecordSetLocalService.deleteRecordSet(recordSet);
	}

	@Override
	public void deleteStagedModel(
			String uuid, long groupId, String className, String extraData)
		throws PortalException {

		DDLRecordSet ddlRecordSet = fetchStagedModelByUuidAndGroupId(
			uuid, groupId);

		if (ddlRecordSet != null) {
			deleteStagedModel(ddlRecordSet);
		}
	}

	@Override
	public DDLRecordSet fetchStagedModelByUuidAndGroupId(
		String uuid, long groupId) {

		return _ddlRecordSetLocalService.fetchDDLRecordSetByUuidAndGroupId(
			uuid, groupId);
	}

	@Override
	public List<DDLRecordSet> fetchStagedModelsByUuidAndCompanyId(
		String uuid, long companyId) {

		return _ddlRecordSetLocalService.getDDLRecordSetsByUuidAndCompanyId(
			uuid, companyId, QueryUtil.ALL_POS, QueryUtil.ALL_POS,
			new StagedModelModifiedDateComparator<DDLRecordSet>());
	}

	@Override
	public String[] getClassNames() {
		return CLASS_NAMES;
	}

	@Override
	public String getDisplayName(DDLRecordSet recordSet) {
		return recordSet.getNameCurrentValue();
	}

	@Override
	protected void doExportStagedModel(
			PortletDataContext portletDataContext, DDLRecordSet recordSet)
		throws Exception {

		DDMStructure ddmStructure = recordSet.getDDMStructure();

		StagedModelDataHandlerUtil.exportReferenceStagedModel(
			portletDataContext, recordSet, ddmStructure,
			PortletDataContext.REFERENCE_TYPE_STRONG);

		List<DDMTemplate> ddmTemplates = ddmStructure.getTemplates();

		Element recordSetElement = portletDataContext.getExportDataElement(
			recordSet);

		for (DDMTemplate ddmTemplate : ddmTemplates) {
			StagedModelDataHandlerUtil.exportReferenceStagedModel(
				portletDataContext, recordSet, ddmTemplate,
				PortletDataContext.REFERENCE_TYPE_STRONG);
		}

		if (recordSet.getScope() == DDLRecordSetConstants.SCOPE_FORMS) {
			exportRecordSetSettings(
				portletDataContext, recordSet, recordSetElement);
		}

		portletDataContext.addClassedModel(
			recordSetElement, ExportImportPathUtil.getModelPath(recordSet),
			recordSet);
	}

	@Override
	protected void doImportMissingReference(
			PortletDataContext portletDataContext, String uuid, long groupId,
			long recordSetId)
		throws Exception {

		DDLRecordSet existingRecordSet = fetchMissingReference(uuid, groupId);

		Map<Long, Long> recordSetIds =
			(Map<Long, Long>)portletDataContext.getNewPrimaryKeysMap(
				DDLRecordSet.class);

		recordSetIds.put(recordSetId, existingRecordSet.getRecordSetId());
	}

	@Override
	protected void doImportStagedModel(
			PortletDataContext portletDataContext, DDLRecordSet recordSet)
		throws Exception {

		long userId = portletDataContext.getUserId(recordSet.getUserUuid());

		Map<Long, Long> ddmStructureIds =
			(Map<Long, Long>)portletDataContext.getNewPrimaryKeysMap(
				DDMStructure.class);

		long ddmStructureId = MapUtil.getLong(
			ddmStructureIds, recordSet.getDDMStructureId(),
			recordSet.getDDMStructureId());

		Element recordSetElement = portletDataContext.getImportDataElement(
			recordSet);

		ServiceContext serviceContext = portletDataContext.createServiceContext(
			recordSet);

		DDLRecordSet importedRecordSet = null;

		if (portletDataContext.isDataStrategyMirror()) {
			DDLRecordSet existingRecordSet = fetchStagedModelByUuidAndGroupId(
				recordSet.getUuid(), portletDataContext.getScopeGroupId());

			if (existingRecordSet == null) {
				serviceContext.setUuid(recordSet.getUuid());

				importedRecordSet = _ddlRecordSetLocalService.addRecordSet(
					userId, portletDataContext.getScopeGroupId(),
					ddmStructureId, recordSet.getRecordSetKey(),
					recordSet.getNameMap(), recordSet.getDescriptionMap(),
					recordSet.getMinDisplayRows(), recordSet.getScope(),
					serviceContext);
			}
			else {
				importedRecordSet = _ddlRecordSetLocalService.updateRecordSet(
					existingRecordSet.getRecordSetId(), ddmStructureId,
					recordSet.getNameMap(), recordSet.getDescriptionMap(),
					recordSet.getMinDisplayRows(), serviceContext);
			}
		}
		else {
			importedRecordSet = _ddlRecordSetLocalService.addRecordSet(
				userId, portletDataContext.getScopeGroupId(), ddmStructureId,
				recordSet.getRecordSetKey(), recordSet.getNameMap(),
				recordSet.getDescriptionMap(), recordSet.getMinDisplayRows(),
				recordSet.getScope(), serviceContext);
		}

		if (recordSet.getScope() == DDLRecordSetConstants.SCOPE_FORMS) {
			DDMFormValues settingsDDMFormValues = getImportRecordSetSettings(
				portletDataContext, recordSetElement);

			_ddlRecordSetLocalService.updateRecordSet(
				importedRecordSet.getRecordSetId(), settingsDDMFormValues);
		}

		portletDataContext.importClassedModel(recordSet, importedRecordSet);
	}

	protected void exportRecordSetSettings(
		PortletDataContext portletDataContext, DDLRecordSet recordSet,
		Element recordSetElement) {

		String settingsDDMFormValuesPath = ExportImportPathUtil.getModelPath(
			recordSet, "settings-ddm-form-values.json");

		recordSetElement.addAttribute(
			"settings-ddm-form-values-path", settingsDDMFormValuesPath);

		portletDataContext.addZipEntry(
			settingsDDMFormValuesPath, recordSet.getSettings());
	}

	protected DDMFormValues getImportRecordSetSettings(
			PortletDataContext portletDataContext, Element recordSetElement)
		throws Exception {

		DDMForm ddmForm = DDMFormFactory.create(DDLRecordSetSettings.class);

		String settingsDDMFormValuesPath = recordSetElement.attributeValue(
			"settings-ddm-form-values-path");

		String serializedSettingsDDMFormValues =
			portletDataContext.getZipEntryAsString(settingsDDMFormValuesPath);

		return _ddmFormValuesJSONDeserializer.deserialize(
			ddmForm, serializedSettingsDDMFormValues);
	}

	@Reference(unbind = "-")
	protected void setDDLRecordSetLocalService(
		DDLRecordSetLocalService ddlRecordSetLocalService) {

		_ddlRecordSetLocalService = ddlRecordSetLocalService;
	}

	@Reference(unbind = "-")
	protected void setDDMFormValuesJSONDeserializer(
		DDMFormValuesJSONDeserializer ddmFormValuesJSONDeserializer) {

		_ddmFormValuesJSONDeserializer = ddmFormValuesJSONDeserializer;
	}

	private DDLRecordSetLocalService _ddlRecordSetLocalService;
	private DDMFormValuesJSONDeserializer _ddmFormValuesJSONDeserializer;

}