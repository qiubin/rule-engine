import React, { useState, useEffect } from 'react'
import { Card, Table, Button, Modal, Form, Input, Select, Tag, message, Popconfirm } from 'antd'
import { PlusOutlined, EditOutlined, DeleteOutlined } from '@ant-design/icons'
import axios from 'axios'

const CAT_API = '/api/v1/condition-model-categories'
const DE_API = '/api/v1/data-elements'
const DS_API = '/api/v1/data-sets'
const CM_API = '/api/v1/condition-models'

// 数据类型与默认计算符的映射
const TYPE_OPERATORS = {
  'NUMERIC': ['==', '!=', '>', '<'],
  'STRING': ['==', '!=', 'contains', 'regex_match'],
  'DICTIONARY': ['==', '!=', 'IN_SET'],
  'DICTIONARY_SET': ['IN_SET'],
  'DATE_TIME': ['==', '!=', '>', '<'],
  'SCRIPT': ['==']
}

export default function ConditionModelMgr() {
  // 条件分类
  const [categories, setCategories] = useState([])
  const [catModalOpen, setCatModalOpen] = useState(false)
  const [editingCat, setEditingCat] = useState(null)
  const [catForm] = Form.useForm()

  // 条件
  const [conditions, setConditions] = useState([])
  const [dataElements, setDataElements] = useState([])
  const [dataSets, setDataSets] = useState([])
  const [selectedCatId, setSelectedCatId] = useState(null)
  const [loading, setLoading] = useState(false)

  const [cmModalOpen, setCmModalOpen] = useState(false)
  const [editingCm, setEditingCm] = useState(null)
  const [cmForm] = Form.useForm()

  const fetchData = async () => {
    setLoading(true)
    try {
      const [cmRes, catRes, deRes, dsRes] = await Promise.all([
        axios.get(CM_API), axios.get(CAT_API), axios.get(DE_API), axios.get(DS_API)
      ])
      setConditions(cmRes.data)
      setCategories(catRes.data)
      setDataElements(deRes.data)
      setDataSets(dsRes.data)
      if (catRes.data.length > 0 && !selectedCatId) {
        setSelectedCatId(catRes.data[0].id)
      }
    } catch (e) {
      message.error('加载失败')
    }
    setLoading(false)
  }

  useEffect(() => { fetchData() }, [])

  const filteredConditions = selectedCatId
    ? conditions.filter(c => c.categoryId === selectedCatId)
    : conditions

  // ========== 条件分类操作 ==========
  const handleSaveCat = async () => {
    const values = await catForm.validateFields()
    try {
      if (editingCat) {
        await axios.put(`${CAT_API}/${editingCat.id}`, { ...editingCat, ...values })
      } else {
        await axios.post(CAT_API, values)
      }
      message.success('保存成功')
      setCatModalOpen(false)
      fetchData()
    } catch (e) {
      message.error(e.response?.data?.message || '保存失败')
    }
  }

  const handleDeleteCat = async (id) => {
    try {
      await axios.delete(`${CAT_API}/${id}`)
      message.success('删除成功')
      if (selectedCatId === id) setSelectedCatId(null)
      fetchData()
    } catch (e) {
      message.error(e.response?.data?.message || '删除失败')
    }
  }

  // ========== 条件操作 ==========
  const handleSaveCm = async () => {
    const values = await cmForm.validateFields()
    try {
      const payload = {
        ...values,
        categoryId: selectedCatId,
        operators: values.operators || [],
      }
      if (editingCm) {
        await axios.put(`${CM_API}/${editingCm.id}`, { ...editingCm, ...payload })
      } else {
        await axios.post(CM_API, payload)
      }
      message.success('保存成功')
      setCmModalOpen(false)
      fetchData()
    } catch (e) {
      message.error(e.response?.data?.message || '保存失败')
    }
  }

  const handleDeleteCm = async (id) => {
    try {
      await axios.delete(`${CM_API}/${id}`)
      message.success('删除成功')
      fetchData()
    } catch (e) {
      message.error(e.response?.data?.message || '删除失败')
    }
  }

  const onDataElementChange = (deId) => {
    const de = dataElements.find(d => d.id === deId)
    if (de) {
      const defaultOps = TYPE_OPERATORS[de.dataType] || []
      cmForm.setFieldsValue({ 
        code: de.code, 
        name: de.name, 
        dataType: de.dataType,
        operators: defaultOps
      })
    }
  }

  const onNodeUsageChange = (usage) => {
    if (usage === 'RESULT') {
      cmForm.setFieldsValue({ dataElementId: undefined, code: undefined, name: undefined, dataType: undefined })
    }
  }

  const onDatasetChange = () => {
    cmForm.setFieldsValue({ dataElementId: undefined, code: undefined, name: undefined, dataType: undefined })
  }

  const getDeName = (id) => dataElements.find(d => d.id === id)?.name || '-'
  const getCatName = (id) => categories.find(c => c.id === id)?.name || '-'

  // 条件分类表格
  const catColumns = [
    { title: 'ID', dataIndex: 'id', width: 50 },
    { title: '编码', dataIndex: 'code' },
    { title: '名称', dataIndex: 'name' },
    { title: '描述', dataIndex: 'description', ellipsis: true },
    { title: '排序', dataIndex: 'sortOrder', width: 70 },
    {
      title: '操作', width: 120,
      render: (_, record) => (
        <>
          <Button type="link" size="small" icon={<EditOutlined />} onClick={() => {
            setEditingCat(record)
            catForm.setFieldsValue(record)
            setCatModalOpen(true)
          }} />
          <Popconfirm title="确认删除?" onConfirm={() => handleDeleteCat(record.id)}>
            <Button type="link" size="small" danger icon={<DeleteOutlined />} />
          </Popconfirm>
        </>
      )
    }
  ]

  // 条件表格
  const cmColumns = [
    { title: 'ID', dataIndex: 'id', width: 50 },
    { title: '编码', dataIndex: 'code' },
    { title: '名称', dataIndex: 'name' },
    { title: '分类', dataIndex: 'categoryId', render: (v) => getCatName(v) },
    { title: '数据元', dataIndex: 'dataElementId', render: (v) => getDeName(v) },
    { title: '数据类型', dataIndex: 'dataType', width: 90 },
    { title: '计算符', dataIndex: 'operators', render: (ops) => ops?.map(o => <Tag key={o}>{o}</Tag>) },
    { title: '用途', dataIndex: 'nodeUsage', width: 80, render: (v) => (
      <Tag color={v === 'CONDITION' ? 'blue' : v === 'RESULT' ? 'orange' : 'green'}>
        {v === 'CONDITION' ? '条件' : v === 'RESULT' ? '结果' : '通用'}
      </Tag>
    )},
    {
      title: '操作', width: 120,
      render: (_, record) => (
        <>
          <Button type="link" size="small" icon={<EditOutlined />} onClick={() => {
            setEditingCm(record)
            cmForm.setFieldsValue({
              ...record,
              operators: record.operators || [],
            })
            setCmModalOpen(true)
          }} />
          <Popconfirm title="确认删除?" onConfirm={() => handleDeleteCm(record.id)}>
            <Button type="link" size="small" danger icon={<DeleteOutlined />} />
          </Popconfirm>
        </>
      )
    }
  ]

  return (
    <div style={{ padding: 24 }}>
      <h2>条件分类与条件管理</h2>

      {/* 条件分类 */}
      <Card title="条件分类" size="small" style={{ marginBottom: 16 }}
        extra={
          <Button type="primary" size="small" icon={<PlusOutlined />} onClick={() => {
            setEditingCat(null)
            catForm.resetFields()
            setCatModalOpen(true)
          }}>新增分类</Button>
        }
      >
        <Table rowKey="id" size="small" columns={catColumns} dataSource={categories} pagination={false} bordered />
      </Card>

      {/* 条件 */}
      <Card title="条件管理" size="small"
        extra={
          <div style={{ display: 'flex', gap: 8 }}>
            <Select
              placeholder="选择分类筛选"
              style={{ width: 200 }}
              value={selectedCatId}
              onChange={setSelectedCatId}
              allowClear
            >
              {categories.map(c => <Select.Option key={c.id} value={c.id}>{c.name}</Select.Option>)}
            </Select>
            <Button type="primary" size="small" icon={<PlusOutlined />} disabled={!selectedCatId} onClick={() => {
              setEditingCm(null)
              cmForm.resetFields()
              setCmModalOpen(true)
            }}>新增条件</Button>
          </div>
        }
      >
        <Table rowKey="id" size="small" columns={cmColumns} dataSource={filteredConditions} loading={loading} bordered />
      </Card>

      {/* 分类弹窗 */}
      <Modal title={editingCat ? '编辑分类' : '新增分类'} open={catModalOpen} onOk={handleSaveCat} onCancel={() => setCatModalOpen(false)}>
        <Form form={catForm} layout="vertical">
          <Form.Item name="code" label="分类编码" rules={[{ required: true }]}>
            <Input disabled={!!editingCat} />
          </Form.Item>
          <Form.Item name="name" label="分类名称" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <Input.TextArea rows={2} />
          </Form.Item>
          <Form.Item name="sortOrder" label="排序">
            <Input type="number" min={0} />
          </Form.Item>
        </Form>
      </Modal>

      {/* 条件弹窗 */}
      <Modal title={editingCm ? '编辑条件' : '新增条件'} open={cmModalOpen} onOk={handleSaveCm} onCancel={() => setCmModalOpen(false)} width={700}>
        {selectedCatId && (
          <div style={{ marginBottom: 16, padding: '8px 12px', background: '#e6f7ff', borderRadius: 4 }}>
            所属分类：<b>{getCatName(selectedCatId)}</b>
          </div>
        )}
        <Form form={cmForm} layout="vertical">
          <Form.Item name="nodeUsage" label="节点用途" rules={[{ required: true }]}>
            <Select placeholder="选择用途" onChange={onNodeUsageChange}>
              <Select.Option value="CONDITION">条件节点</Select.Option>
              <Select.Option value="RESULT">结果节点</Select.Option>
              <Select.Option value="BOTH">通用</Select.Option>
            </Select>
          </Form.Item>
          <Form.Item name="datasetId" label="选择数据集">
            <Select placeholder="先选择数据集" onChange={onDatasetChange} allowClear>
              {dataSets.map(ds => <Select.Option key={ds.id} value={ds.id}>{ds.name}</Select.Option>)}
            </Select>
          </Form.Item>
          <Form.Item name="dataElementId" label="选择数据元" rules={[{ required: false }]}>
            <Select placeholder="选择数据元，自动填充编码和名称" onChange={onDataElementChange} allowClear>
              {dataElements
                .filter(de => {
                  const selectedDs = cmForm.getFieldValue('datasetId')
                  return !selectedDs || de.datasetId === selectedDs
                })
                .map(de => (
                  <Select.Option key={de.id} value={de.id}>{de.name} ({de.code})</Select.Option>
                ))}
            </Select>
          </Form.Item>
          <Form.Item name="code" label="条件编码"><Input placeholder="自动来自数据元编码，可手动输入" /></Form.Item>
          <Form.Item name="name" label="条件名称"><Input placeholder="自动来自数据元名称，可手动输入" /></Form.Item>
          <Form.Item name="dataType" label="数据类型"><Input disabled placeholder="自动来自数据元类型" /></Form.Item>
          <Form.Item name="operators" label="计算符" rules={[{ required: false }]}>
            <Select mode="multiple" placeholder="选择支持的计算符">
              <Select.Option value="==">等于</Select.Option>
              <Select.Option value="!=">不等于</Select.Option>
              <Select.Option value=">">大于</Select.Option>
              <Select.Option value="<">小于</Select.Option>
              <Select.Option value="contains">包含</Select.Option>
              <Select.Option value="regex_match">正则匹配</Select.Option>
              <Select.Option value="IN_SET">在集合中</Select.Option>
            </Select>
          </Form.Item>
          <Form.Item name="valueSource" label="值来源" rules={[{ required: true }]}>
            <Select placeholder="选择值来源">
              <Select.Option value="PARAM">参数</Select.Option>
              <Select.Option value="SQL">SQL查询</Select.Option>
              <Select.Option value="ADAPTER">适配器</Select.Option>
            </Select>
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}
