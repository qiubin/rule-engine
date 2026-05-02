import React, { useEffect, useState } from 'react'
import { Drawer, Form, Input, Select, Button, Radio, Tag } from 'antd'
import { SaveOutlined } from '@ant-design/icons'
import axios from 'axios'

const { Option, OptGroup } = Select

export default function ConfigPanel({ open, onClose, node, onUpdate }) {
  const [form] = Form.useForm()
  const [categories, setCategories] = useState([])
  const [conditions, setConditions] = useState([])
  const [dictionaries, setDictionaries] = useState([])
  const [selectedCategoryId, setSelectedCategoryId] = useState(null)
  const [selectedOperator, setSelectedOperator] = useState(null)

  useEffect(() => {
    if (node && open) {
      form.setFieldsValue({
        label: node.data?.label || '',
        ...node.data?.conditionConfig,
        ...node.data?.resultConfig,
      })
      setSelectedOperator(node.data?.conditionConfig?.operator || null)
      if (node.type === 'condition' || node.type === 'result') {
        fetchCategories()
        fetchDictionaries()
        const savedCategoryId = node.data?.conditionConfig?.categoryId || node.data?.resultConfig?.categoryId
        if (savedCategoryId) {
          setSelectedCategoryId(savedCategoryId)
          fetchConditionsByCategory(savedCategoryId)
        }
      }
    }
  }, [node, open, form])

  const fetchCategories = async () => {
    try {
      const res = await axios.get('/api/v1/condition-model-categories')
      setCategories(res.data)
    } catch (e) {}
  }

  const fetchConditionsByCategory = async (categoryId) => {
    try {
      const res = await axios.get(`/api/v1/condition-models/by-category/${categoryId}`)
      const nodeUsage = node?.type === 'condition' ? 'CONDITION' : 'RESULT'
      const filtered = res.data.filter(m =>
        m.nodeUsage === nodeUsage || m.nodeUsage === 'BOTH'
      )
      setConditions(filtered)
    } catch (e) {}
  }

  const fetchDictionaries = async () => {
    try {
      const res = await axios.get('/api/v1/dictionaries')
      setDictionaries(res.data)
    } catch (e) {}
  }

  const onCategoryChange = (categoryId) => {
    setSelectedCategoryId(categoryId)
    form.setFieldsValue({ conditionModelId: undefined, field: undefined, dataType: undefined })
    fetchConditionsByCategory(categoryId)
  }

  const onConditionChange = (modelId) => {
    const model = conditions.find(m => m.id === modelId)
    if (model && model.dataElementId) {
      form.setFieldsValue({
        field: model.code,
        dataType: model.dataType,
      })
    }
  }

  const handleSave = () => {
    const values = form.getFieldsValue()
    if (!node) return

    const newData = { label: values.label }

    if (node.type === 'condition') {
      newData.conditionConfig = {
        field: values.field,
        operator: values.operator,
        value: values.value,
        extraValue1: values.extraValue1,
        extraValue2: values.extraValue2,
        dictCode: values.dictCode,
        allDictCode: values.allDictCode,
        dictAttr: values.dictAttr,
        allDictAttr: values.allDictAttr,
        valueSource: values.valueSource,
        conditionModelId: values.conditionModelId,
        categoryId: values.categoryId,
      }
    } else if (node.type === 'result') {
      newData.resultConfig = {
        resultType: values.resultType,
        resultValue: values.resultValue,
        priority: values.priority,
        conditionModelId: values.conditionModelId,
        categoryId: values.categoryId,
      }
    }

    onUpdate(node.id, newData)
  }

  const DictSelect = ({ name, label }) => (
    <Form.Item name={name} label={label} style={{ marginTop: -8 }}>
      <Select placeholder="选择字典" allowClear>
        {dictionaries.map(dict => (
          <Option key={dict.code} value={dict.code}>
            {dict.name} ({dict.items?.length || 0}项)
          </Option>
        ))}
      </Select>
    </Form.Item>
  )

  const DictAttrSelect = ({ name }) => (
    <Form.Item name={name} label="匹配属性" style={{ marginTop: -8 }}>
      <Select placeholder="默认名称" allowClear>
        <Option value="itemName">名称</Option>
        <Option value="itemCode">编码</Option>
        <Option value="itemValue">值</Option>
      </Select>
    </Form.Item>
  )

  const renderConditionForm = () => (
    <>
      <Form.Item name="categoryId" label="条件分类" rules={[{ required: true, message: '必须选择条件分类' }]}>
        <Select placeholder="先选择条件分类" onChange={onCategoryChange}>
          {categories.map(cat => (
            <Option key={cat.id} value={cat.id}>{cat.name}</Option>
          ))}
        </Select>
      </Form.Item>
      <Form.Item name="conditionModelId" label="条件" rules={[{ required: true, message: '必须选择条件' }]}>
        <Select placeholder={selectedCategoryId ? '选择该分类下的条件' : '请先选择分类'} disabled={!selectedCategoryId} onChange={onConditionChange}>
          {conditions.map(m => (
            <Option key={m.id} value={m.id}>
              {m.name}
              <Tag color={m.nodeUsage === 'CONDITION' ? 'blue' : m.nodeUsage === 'BOTH' ? 'green' : 'orange'} style={{ marginLeft: 8 }}>
                {m.nodeUsage === 'CONDITION' ? '条件' : m.nodeUsage === 'RESULT' ? '结果' : '通用'}
              </Tag>
            </Option>
          ))}
        </Select>
      </Form.Item>
      <Form.Item name="field" label="条件字段">
        <Input disabled placeholder="自动来自条件关联的数据元编码" />
      </Form.Item>
      <Form.Item name="dataType" label="数据类型">
        <Input disabled placeholder="自动来自条件" />
      </Form.Item>
      <Form.Item name="operator" label="计算符" rules={[{ required: true }]}>
        <Select placeholder="选择计算符" onChange={(val) => setSelectedOperator(val)}>
          <OptGroup label="通用计算符">
            <Option value="==">等于</Option>
            <Option value="!=">不等于</Option>
            <Option value=">">大于</Option>
            <Option value="<">小于</Option>
            <Option value="contains">包含</Option>
            <Option value="regex_match">原生正则</Option>
            <Option value="IN_SET">在集合中</Option>
          </OptGroup>
          <OptGroup label="脚本计算符">
            <Option value="regexMatch">单正则匹配</Option>
            <Option value="multiRegexMatch">多条件正则</Option>
            <Option value="contradictionCheck">矛盾判断</Option>
            <Option value="existenceConflict">存在性冲突</Option>
            <Option value="whitelistMatch">白名单匹配</Option>
            <Option value="dictMatch">字典匹配</Option>
            <Option value="dataCheck">数据判断</Option>
            <Option value="timeCheck">时间判断</Option>
          </OptGroup>
        </Select>
      </Form.Item>
      {selectedOperator === 'multiRegexMatch' ? (
        <>
          <Form.Item name="value" label="抗菌药物集合">
            <Input placeholder="手工输入，如: 青霉素,头孢" />
          </Form.Item>
          <DictSelect name="dictCode" label="或选择字典" />
          <DictAttrSelect name="dictAttr" />
          <Form.Item name="extraValue1" label="病程记录字段名" rules={[{ required: true }]}>
            <Input placeholder="如: courseRecord" />
          </Form.Item>
        </>
      ) : selectedOperator === 'contradictionCheck' ? (
        <>
          <Form.Item name="value" label="否定词库">
            <Input placeholder="手工输入，如: 否认,无" />
          </Form.Item>
          <DictSelect name="dictCode" label="或选择否定词字典" />
          <DictAttrSelect name="dictAttr" />
          <Form.Item name="extraValue1" label="关键字库">
            <Input placeholder="手工输入，如: 发热,头痛" />
          </Form.Item>
          <DictSelect name="allDictCode" label="或选择关键字字典" />
          <DictAttrSelect name="allDictAttr" />
          <Form.Item name="extraValue2" label="对比字段名" rules={[{ required: true }]}>
            <Input placeholder="如: 入院主诉" />
          </Form.Item>
        </>
      ) : selectedOperator === 'existenceConflict' ? (
        <>
          <Form.Item name="value" label="冲突关键词列表">
            <Input placeholder="手工输入，如: 糖尿病,高血压,发热" />
          </Form.Item>
          <DictSelect name="dictCode" label="或选择关键词字典" />
          <DictAttrSelect name="dictAttr" />
          <Form.Item name="extraValue1" label="对比字段名" rules={[{ required: true }]}>
            <Input placeholder="如: 入院主诉" />
          </Form.Item>
        </>
      ) : selectedOperator === 'whitelistMatch' ? (
        <>
          <Form.Item name="value" label="允许的关键词列表">
            <Input placeholder="手工输入，如: 头痛,发热" />
          </Form.Item>
          <Form.Item name="extraValue1" label="全部关键词列表">
            <Input placeholder="手工输入，如: 头痛,发热,咳嗽,流涕；留空则只做正向包含检查" />
          </Form.Item>
        </>
      ) : selectedOperator === 'dictMatch' ? (
        <>
          <DictSelect name="dictCode" label="允许关键词字典" />
          <DictAttrSelect name="dictAttr" />
        </>
      ) : selectedOperator === 'dataCheck' ? (
        <>
          <Form.Item name="value" label="操作符" rules={[{ required: true }]}>
            <Select placeholder="如: >">
               <Option value="==">==</Option>
               <Option value="!=">!=</Option>
               <Option value=">">&gt;</Option>
               <Option value="<">&lt;</Option>
               <Option value=">=">&gt;=</Option>
               <Option value="<=">&lt;=</Option>
            </Select>
          </Form.Item>
          <Form.Item name="extraValue1" label="阈值" rules={[{ required: true }]}>
            <Input placeholder="如: 50" />
          </Form.Item>
        </>
      ) : selectedOperator === 'timeCheck' ? (
        <>
          <Form.Item name="value" label="基准时间字段" rules={[{ required: true }]}>
            <Input placeholder="如: baseTime" />
          </Form.Item>
          <Form.Item name="extraValue1" label="最小相差小时数">
            <Input placeholder="留空则不限制" />
          </Form.Item>
          <Form.Item name="extraValue2" label="最大相差小时数">
            <Input placeholder="留空则不限制" />
          </Form.Item>
        </>
      ) : selectedOperator === 'regexMatch' ? (
        <>
          <Form.Item name="value" label="正则表达式">
            <Input placeholder="如: ^[a-z]+$" />
          </Form.Item>
          <DictSelect name="dictCode" label="或选择正则字典" />
          <DictAttrSelect name="dictAttr" />
        </>
      ) : (
        <Form.Item name="value" label="条件值" rules={[{ required: true }]}>
          <Input placeholder="如: 65" />
        </Form.Item>
      )}
      <Form.Item name="valueSource" label="值来源">
        <Radio.Group>
          <Radio value="PARAM">参数</Radio>
          <Radio value="SQL">SQL查询</Radio>
          <Radio value="ADAPTER">适配器</Radio>
        </Radio.Group>
      </Form.Item>
    </>
  )

  const renderResultForm = () => (
    <>
      <Form.Item name="categoryId" label="条件分类" rules={[{ required: true, message: '必须选择条件分类' }]}>
        <Select placeholder="先选择条件分类" onChange={onCategoryChange}>
          {categories.map(cat => (
            <Option key={cat.id} value={cat.id}>{cat.name}</Option>
          ))}
        </Select>
      </Form.Item>
      <Form.Item name="conditionModelId" label="结果条件" rules={[{ required: true, message: '必须选择结果条件' }]}>
        <Select placeholder={selectedCategoryId ? '选择该分类下的结果条件' : '请先选择分类'} disabled={!selectedCategoryId}>
          {conditions.map(m => (
            <Option key={m.id} value={m.id}>
              {m.name}
              <Tag color={m.nodeUsage === 'RESULT' ? 'orange' : m.nodeUsage === 'BOTH' ? 'green' : 'blue'} style={{ marginLeft: 8 }}>
                {m.nodeUsage === 'CONDITION' ? '条件' : m.nodeUsage === 'RESULT' ? '结果' : '通用'}
              </Tag>
            </Option>
          ))}
        </Select>
      </Form.Item>
      <Form.Item name="resultType" label="结果类型" rules={[{ required: true }]}>
        <Select placeholder="选择结果类型">
          <Option value="FORBIDDEN">绝对禁忌</Option>
          <Option value="WARNING">相对禁忌</Option>
          <Option value="ALERT">预警提醒</Option>
          <Option value="MESSAGE">消息提醒</Option>
          <Option value="DRUG_FORBIDDEN">禁用</Option>
          <Option value="DRUG_CAUTION">慎用</Option>
          <Option value="NURSING_LEVEL_1">特级护理</Option>
          <Option value="NURSING_LEVEL_2">一级护理</Option>
        </Select>
      </Form.Item>
      <Form.Item name="resultValue" label="结果值" rules={[{ required: true }]}>
        <Input placeholder="结果描述" />
      </Form.Item>
      <Form.Item name="priority" label="优先级">
        <Input type="number" placeholder="0-100" />
      </Form.Item>
    </>
  )

  const titleMap = {
    condition: '配置条件节点',
    result: '配置结果节点',
    and: '配置AND节点',
    or: '配置OR节点',
  }

  if (!node) return null

  return (
    <Drawer
      title={titleMap[node.type] || '节点配置'}
      width={420}
      open={open}
      onClose={onClose}
      footer={
        <Button type="primary" icon={<SaveOutlined />} onClick={handleSave} block>
          保存配置
        </Button>
      }
    >
      <Form form={form} layout="vertical">
        <Form.Item name="label" label="节点名称" rules={[{ required: true }]}>
          <Input placeholder="输入节点名称" />
        </Form.Item>

        {node.type === 'condition' && renderConditionForm()}
        {node.type === 'result' && renderResultForm()}
        {(node.type === 'and' || node.type === 'or') && (
          <div style={{ color: '#999', textAlign: 'center', padding: 20 }}>
            逻辑节点无需额外配置
          </div>
        )}
      </Form>
    </Drawer>
  )
}
