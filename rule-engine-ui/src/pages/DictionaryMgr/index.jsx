import React, { useState, useEffect } from 'react'
import { Table, Button, Modal, Form, Input, Tabs, message, Popconfirm } from 'antd'
import { PlusOutlined, EditOutlined, DeleteOutlined } from '@ant-design/icons'
import axios from 'axios'

const API = '/api/v1/dictionaries'
const ITEM_API = '/api/v1/dictionary-items'

export default function DictionaryMgr() {
  const [dicts, setDicts] = useState([])
  const [items, setItems] = useState([])
  const [loading, setLoading] = useState(false)
  const [dictModalOpen, setDictModalOpen] = useState(false)
  const [itemModalOpen, setItemModalOpen] = useState(false)
  const [editingDict, setEditingDict] = useState(null)
  const [editingItem, setEditingItem] = useState(null)
  const [selectedDict, setSelectedDict] = useState(null)
  const [dictForm] = Form.useForm()
  const [itemForm] = Form.useForm()

  const fetchDicts = async () => {
    setLoading(true)
    try {
      const res = await axios.get(API)
      setDicts(res.data)
    } catch (e) { message.error('加载失败') }
    setLoading(false)
  }

  const fetchItems = async (dictCode) => {
    if (!dictCode) return
    try {
      const res = await axios.get(`${ITEM_API}/by-dict-code/${dictCode}`)
      setItems(res.data)
    } catch (e) { message.error('加载字典项失败') }
  }

  useEffect(() => { fetchDicts() }, [])

  const handleSaveDict = async () => {
    const values = await dictForm.validateFields()
    try {
      if (editingDict) {
        await axios.put(`${API}/${editingDict.id}`, { ...editingDict, ...values })
      } else {
        await axios.post(API, values)
      }
      message.success('保存成功')
      setDictModalOpen(false)
      fetchDicts()
    } catch (e) {
      message.error('保存失败: ' + (e.response?.data?.message || e.message))
    }
  }

  const handleDeleteDict = async (id) => {
    try {
      await axios.delete(`${API}/${id}`)
      message.success('删除成功')
      fetchDicts()
    } catch (e) { message.error(e.response?.data?.message || '删除失败') }
  }

  const handleSaveItem = async () => {
    const values = await itemForm.validateFields()
    const payload = { ...values, dictCode: selectedDict.code, dictionaryId: selectedDict.id }
    try {
      if (editingItem) {
        await axios.put(`${ITEM_API}/${editingItem.id}`, { ...editingItem, ...payload })
      } else {
        await axios.post(ITEM_API, payload)
      }
      message.success('保存成功')
      setItemModalOpen(false)
      fetchItems(selectedDict.code)
    } catch (e) {
      message.error('保存失败: ' + (e.response?.data?.message || e.message))
    }
  }

  const handleDeleteItem = async (id) => {
    try {
      await axios.delete(`${ITEM_API}/${id}`)
      message.success('删除成功')
      fetchItems(selectedDict.code)
    } catch (e) { message.error(e.response?.data?.message || '删除失败') }
  }

  const dictColumns = [
    { title: 'ID', dataIndex: 'id', width: 60 },
    { title: '编码', dataIndex: 'code' },
    { title: '名称', dataIndex: 'name' },
    { title: '描述', dataIndex: 'description', ellipsis: true },
    {
      title: '操作', width: 200,
      render: (_, record) => (
        <>
          <Button type="link" icon={<EditOutlined />} onClick={() => {
            setEditingDict(record)
            dictForm.setFieldsValue(record)
            setDictModalOpen(true)
          }}>编辑</Button>
          <Button type="link" onClick={() => {
            setSelectedDict(record)
            fetchItems(record.code)
          }}>管理项</Button>
          <Popconfirm title="确认删除?" onConfirm={() => handleDeleteDict(record.id)}>
            <Button type="link" danger icon={<DeleteOutlined />}>删除</Button>
          </Popconfirm>
        </>
      )
    }
  ]

  const itemColumns = [
    { title: '项编码', dataIndex: 'itemCode' },
    { title: '项名称', dataIndex: 'itemName' },
    { title: '项值', dataIndex: 'itemValue' },
    { title: '排序', dataIndex: 'sortOrder', width: 80 },
    {
      title: '操作', width: 150,
      render: (_, record) => (
        <>
          <Button type="link" icon={<EditOutlined />} onClick={() => {
            setEditingItem(record)
            itemForm.setFieldsValue(record)
            setItemModalOpen(true)
          }}>编辑</Button>
          <Popconfirm title="确认删除?" onConfirm={() => handleDeleteItem(record.id)}>
            <Button type="link" danger icon={<DeleteOutlined />}>删除</Button>
          </Popconfirm>
        </>
      )
    }
  ]

  return (
    <div style={{ padding: 24 }}>
      <Tabs defaultActiveKey="dict">
        <Tabs.TabPane tab="字典管理" key="dict">
          <div style={{ marginBottom: 16 }}>
            <Button type="primary" icon={<PlusOutlined />} onClick={() => {
              setEditingDict(null)
              dictForm.resetFields()
              setDictModalOpen(true)
            }}>新增字典</Button>
          </div>
          <Table rowKey="id" columns={dictColumns} dataSource={dicts} loading={loading} bordered />
        </Tabs.TabPane>
        <Tabs.TabPane tab={selectedDict ? `字典项: ${selectedDict.name}` : '字典项管理'} key="items" disabled={!selectedDict}>
          <div style={{ marginBottom: 16 }}>
            <Button type="primary" icon={<PlusOutlined />} onClick={() => {
              setEditingItem(null)
              itemForm.resetFields()
              setItemModalOpen(true)
            }}>新增字典项</Button>
          </div>
          <Table rowKey="id" columns={itemColumns} dataSource={items} bordered />
        </Tabs.TabPane>
      </Tabs>

      <Modal title={editingDict ? '编辑字典' : '新增字典'} open={dictModalOpen} onOk={handleSaveDict} onCancel={() => setDictModalOpen(false)}>
        <Form form={dictForm} layout="vertical">
          <Form.Item name="code" label="字典编码" rules={[{ required: true }]}>
            <Input disabled={!!editingDict} />
          </Form.Item>
          <Form.Item name="name" label="字典名称" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <Input.TextArea rows={2} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal title={editingItem ? '编辑字典项' : '新增字典项'} open={itemModalOpen} onOk={handleSaveItem} onCancel={() => setItemModalOpen(false)}>
        <Form form={itemForm} layout="vertical">
          <Form.Item name="itemCode" label="项编码" rules={[{ required: true }]}>
            <Input disabled={!!editingItem} />
          </Form.Item>
          <Form.Item name="itemName" label="项名称" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="itemValue" label="项值">
            <Input />
          </Form.Item>
          <Form.Item name="sortOrder" label="排序">
            <Input type="number" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}
