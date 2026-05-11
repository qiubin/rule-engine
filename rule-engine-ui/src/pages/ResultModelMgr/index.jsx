import React, { useState, useEffect, useMemo } from 'react'
import {
  Layout, Table, Button, Modal, Form, Input, Tag, message, Popconfirm,
  Tree, Space, AutoComplete, Select, Switch, Checkbox
} from 'antd'
import { PlusOutlined, EditOutlined, DeleteOutlined, FolderOutlined } from '@ant-design/icons'
import axios from 'axios'

const { Sider, Content } = Layout
const { Option } = Select
const RC_API = '/api/v1/result-configs'
const DICT_API = '/api/v1/dictionaries'

export default function ResultModelMgr() {
  const [resultConfigs, setResultConfigs] = useState([])
  const [dictionaries, setDictionaries] = useState([])
  const [selectedTypeKey, setSelectedTypeKey] = useState('all')
  const [loading, setLoading] = useState(false)

  const [modalOpen, setModalOpen] = useState(false)
  const [editingRc, setEditingRc] = useState(null)
  const [rcForm] = Form.useForm()

  // 扩展属性
  const [hasExtension, setHasExtension] = useState(false)
  const [extDictCode, setExtDictCode] = useState(null)
  const [extItems, setExtItems] = useState([])

  // 多选弹窗
  const [selectorOpen, setSelectorOpen] = useState(false)
  const [selectorSearch, setSelectorSearch] = useState('')
  const [selectedRowKeys, setSelectedRowKeys] = useState([])

  const typeList = useMemo(() => {
    const types = [...new Set(resultConfigs.map(rc => rc.resultType).filter(Boolean))]
    return types.sort()
  }, [resultConfigs])

  const treeData = useMemo(() => {
    const nodes = [{
      title: '全部',
      key: 'all',
      icon: <FolderOutlined />,
    }]
    for (const type of typeList) {
      nodes.push({
        title: type,
        key: `type-${type}`,
        type,
        icon: <FolderOutlined />,
      })
    }
    return nodes
  }, [typeList])

  const fetchData = async () => {
    setLoading(true)
    try {
      const [rcRes, dictRes] = await Promise.all([
        axios.get(RC_API),
        axios.get(DICT_API)
      ])
      setResultConfigs(rcRes.data)
      setDictionaries(dictRes.data)
    } catch (e) {
      message.error('加载数据失败')
    }
    setLoading(false)
  }

  useEffect(() => { fetchData() }, [])

  const filteredConfigs = useMemo(() => {
    if (selectedTypeKey === 'all') return resultConfigs
    const type = selectedTypeKey.replace('type-', '')
    return resultConfigs.filter(rc => rc.resultType === type)
  }, [resultConfigs, selectedTypeKey])

  const selectedDict = useMemo(() => {
    if (!extDictCode) return null
    return dictionaries.find(d => d.code === extDictCode) || null
  }, [extDictCode, dictionaries])

  const parseMetadata = (str) => {
    if (!str) return {}
    try {
      const meta = JSON.parse(str)
      // 兼容旧格式：单条 extCode/extName 转成 items 数组
      if (meta.hasExtension && !meta.items && meta.extCode) {
        meta.items = [{ code: meta.extCode, name: meta.extName || '' }]
      }
      return meta
    } catch (e) {
      return {}
    }
  }

  // 表单值变化监听
  const handleValuesChange = (changed) => {
    if ('hasExtension' in changed) {
      const checked = !!changed.hasExtension
      setHasExtension(checked)
      if (!checked) {
        rcForm.setFieldsValue({ extDictCode: undefined })
        setExtDictCode(null)
        setExtItems([])
      }
    }
    if ('extDictCode' in changed) {
      const code = changed.extDictCode || null
      setExtDictCode(code)
      setExtItems([])
      setSelectedRowKeys([])
    }
  }

  // === 扩展项操作 ===
  const removeExtItem = (code) => {
    setExtItems(prev => prev.filter(i => i.code !== code))
  }

  const distributeDict = () => {
    if (!selectedDict) return
    const items = selectedDict.items.map(item => ({
      code: item.itemCode,
      name: item.itemName,
    }))
    setExtItems(items)
    message.success(`已分发 ${items.length} 项`)
  }

  const openSelector = () => {
    if (!selectedDict) return
    // 根据已选项的 code 回选
    const keys = selectedDict.items
      .filter(item => extItems.some(ext => ext.code === item.itemCode))
      .map(item => item.id)
    setSelectedRowKeys(keys)
    setSelectorSearch('')
    setSelectorOpen(true)
  }

  const handleSelectorOk = () => {
    if (!selectedDict) return
    const newItems = selectedDict.items
      .filter(item => selectedRowKeys.includes(item.id))
      .map(item => ({ code: item.itemCode, name: item.itemName }))
    // 合并去重
    const map = new Map(extItems.map(i => [i.code, i]))
    for (const item of newItems) {
      if (!map.has(item.code)) map.set(item.code, item)
    }
    setExtItems(Array.from(map.values()))
    setSelectorOpen(false)
  }

  const selectorFilteredItems = useMemo(() => {
    if (!selectedDict) return []
    if (!selectorSearch) return selectedDict.items
    const kw = selectorSearch.toLowerCase()
    return selectedDict.items.filter(item =>
      (item.itemCode || '').toLowerCase().includes(kw) ||
      (item.itemName || '').toLowerCase().includes(kw)
    )
  }, [selectedDict, selectorSearch])

  // === CRUD ===
  const handleSaveRc = async () => {
    const values = await rcForm.validateFields()
    try {
      let metadata = null
      if (values.hasExtension) {
        const meta = { hasExtension: true }
        if (values.extDictCode) meta.dictCode = values.extDictCode
        if (extItems.length > 0) meta.items = extItems
        metadata = JSON.stringify(meta)
      }

      const payload = {
        resultType: values.resultType,
        resultName: values.resultName,
        priority: values.priority,
        content: values.content,
        description: values.description,
        metadata,
      }

      if (editingRc) {
        await axios.put(`${RC_API}/${editingRc.id}`, { ...editingRc, ...payload })
      } else {
        await axios.post(RC_API, payload)
      }
      message.success('保存成功')
      setModalOpen(false)
      fetchData()
    } catch (e) {
      message.error(e.response?.data?.message || '保存失败')
    }
  }

  const handleDeleteRc = async (id) => {
    try {
      await axios.delete(`${RC_API}/${id}`)
      message.success('删除成功')
      fetchData()
    } catch (e) {
      message.error('删除失败')
    }
  }

  const openEditModal = (record) => {
    setEditingRc(record)
    const metadata = parseMetadata(record.metadata)
    const hasExt = metadata.hasExtension || false
    const dictCode = metadata.dictCode || null

    setHasExtension(hasExt)
    setExtDictCode(dictCode)
    setExtItems(metadata.items || [])

    rcForm.setFieldsValue({
      resultType: record.resultType,
      resultName: record.resultName,
      priority: record.priority,
      content: record.content,
      description: record.description,
      hasExtension: hasExt,
      extDictCode: dictCode,
    })
    setModalOpen(true)
  }

  const openAddModal = () => {
    setEditingRc(null)
    setHasExtension(false)
    setExtDictCode(null)
    setExtItems([])
    rcForm.resetFields()
    if (selectedTypeKey && selectedTypeKey.startsWith('type-')) {
      rcForm.setFieldsValue({ resultType: selectedTypeKey.replace('type-', '') })
    }
    setModalOpen(true)
  }

  // === 主表格列 ===
  const columns = [
    { title: 'ID', dataIndex: 'id', width: 60 },
    {
      title: '结果类型(编码)',
      dataIndex: 'resultType',
      width: 140,
      render: v => <Tag color="blue">{v}</Tag>,
    },
    { title: '结果内容(名称)', dataIndex: 'resultName' },
    { title: '优先级', dataIndex: 'priority', width: 70 },
    { title: '结果内容', dataIndex: 'content', ellipsis: true },
    { title: '备注', dataIndex: 'description', ellipsis: true },
    {
      title: '扩展信息', width: 300,
      render: (_, record) => {
        const metadata = parseMetadata(record.metadata)
        if (!metadata.hasExtension) {
          return <span style={{ color: '#bbb' }}>-</span>
        }
        const dict = dictionaries.find(d => d.code === metadata.dictCode)
        const items = metadata.items || []
        return (
          <div style={{ fontSize: 12 }}>
            <div style={{ color: '#666', marginBottom: 4 }}>
              字典: <Tag style={{ fontSize: 11 }}>{dict?.name || metadata.dictCode || '-'}</Tag>
              <span style={{ color: '#999', marginLeft: 8 }}>({items.length}项)</span>
            </div>
            <div style={{ maxHeight: 60, overflow: 'auto', color: '#666' }}>
              {items.map((item, idx) => (
                <span key={idx}>
                  {item.code}-{item.name}{idx < items.length - 1 ? '、' : ''}
                </span>
              ))}
            </div>
          </div>
        )
      },
    },
    {
      title: '操作', width: 100,
      render: (_, record) => (
        <Space>
          <Button type="link" size="small" icon={<EditOutlined />} onClick={() => openEditModal(record)} />
          <Popconfirm title="确认删除?" onConfirm={() => handleDeleteRc(record.id)}>
            <Button type="link" size="small" danger icon={<DeleteOutlined />} />
          </Popconfirm>
        </Space>
      ),
    },
  ]

  const typeOptions = typeList.map(t => ({ value: t }))

  return (
    <div style={{ padding: 24 }}>
      <h2>结果管理</h2>
      <Layout style={{ minHeight: 500, background: '#fff' }}>
        <Sider width={260} style={{ background: '#fafafa', borderRight: '1px solid #f0f0f0', padding: 12 }}>
          <div style={{ color: '#888', fontSize: 13, marginBottom: 12 }}>
            按结果类型筛选
          </div>
          <Tree
            treeData={treeData}
            selectedKeys={[selectedTypeKey]}
            onSelect={(keys) => setSelectedTypeKey(keys[0] || 'all')}
            showIcon
            defaultExpandAll
          />
        </Sider>

        <Content style={{ padding: 16 }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 16 }}>
            <div>
              <h3>结果配置列表</h3>
              <div style={{ color: '#888', fontSize: 13 }}>
                共 {filteredConfigs.length} 条结果配置
                {selectedTypeKey !== 'all' && `（类型：${selectedTypeKey.replace('type-', '')}）`}
              </div>
            </div>
            <Button type="primary" icon={<PlusOutlined />} onClick={openAddModal}>
              新增结果配置
            </Button>
          </div>
          <Table
            rowKey="id"
            columns={columns}
            dataSource={filteredConfigs}
            loading={loading}
            pagination={{ pageSize: 15 }}
          />
        </Content>
      </Layout>

      {/* 结果配置表单 */}
      <Modal
        title={editingRc ? '编辑结果配置' : '新增结果配置'}
        open={modalOpen}
        onOk={handleSaveRc}
        onCancel={() => setModalOpen(false)}
        width={640}
      >
        <Form
          form={rcForm}
          layout="vertical"
          initialValues={{ priority: 0, hasExtension: false }}
          onValuesChange={handleValuesChange}
        >
          <Form.Item
            name="resultType"
            label="结果类型(编码)"
            rules={[{ required: true, message: '请输入结果类型' }]}
          >
            <AutoComplete
              options={typeOptions}
              placeholder="选择已有类型或输入新类型，如：禁忌类型"
              allowClear
            />
          </Form.Item>
          <Form.Item
            name="resultName"
            label="结果内容(名称)"
            rules={[{ required: true, message: '请输入结果名称' }]}
          >
            <Input placeholder="如：绝对禁忌" />
          </Form.Item>
          <Form.Item name="priority" label="优先级" rules={[{ required: true }]}>
            <Input type="number" placeholder="数字越大优先级越高" />
          </Form.Item>

          {/* 扩展属性开关 */}
          <Form.Item label="扩展属性配置" style={{ marginBottom: 8 }}>
            <Form.Item name="hasExtension" valuePropName="checked" noStyle>
              <Switch checkedChildren="开启" unCheckedChildren="关闭" />
            </Form.Item>
            <span style={{ color: '#888', fontSize: 13, marginLeft: 12 }}>
              开启后可关联字典项作为扩展信息
            </span>
          </Form.Item>

          {/* 扩展属性区域 */}
          {hasExtension && (
            <div style={{
              background: '#f6ffed',
              border: '1px solid #b7eb8f',
              borderRadius: 4,
              padding: '12px 16px',
              marginBottom: 16,
            }}>
              <Form.Item
                name="extDictCode"
                label="选择字典"
                rules={[{ required: hasExtension, message: '请选择字典' }]}
              >
                <Select placeholder="请选择字典" allowClear>
                  {dictionaries.map(d => (
                    <Option key={d.code} value={d.code}>
                      {d.name} ({d.items?.length || 0}项)
                    </Option>
                  ))}
                </Select>
              </Form.Item>

              {selectedDict && (
                <>
                  <div style={{ display: 'flex', gap: 8, marginBottom: 12 }}>
                    <Button type="primary" size="small" ghost onClick={openSelector}>
                      <PlusOutlined /> 从字典选择
                    </Button>
                    <Button type="dashed" size="small" onClick={distributeDict}>
                      字典分发（全选{selectedDict.items?.length || 0}项）
                    </Button>
                    {extItems.length > 0 && (
                      <Button size="small" danger onClick={() => setExtItems([])}>
                        清空全部
                      </Button>
                    )}
                  </div>

                  <Table
                    size="small"
                    rowKey="code"
                    columns={[
                      { title: '代码', dataIndex: 'code', width: 100 },
                      { title: '名称', dataIndex: 'name' },
                      {
                        title: '操作', width: 60,
                        render: (_, record) => (
                          <Button
                            type="link"
                            size="small"
                            danger
                            onClick={() => removeExtItem(record.code)}
                          >
                            删除
                          </Button>
                        ),
                      },
                    ]}
                    dataSource={extItems}
                    pagination={false}
                    scroll={{ y: 200 }}
                    locale={{ emptyText: '暂无扩展项，请从字典选择或分发' }}
                  />
                </>
              )}
            </div>
          )}

          <Form.Item name="content" label="结果内容">
            <Input.TextArea placeholder="默认结果提示文案，可在画布中修改" rows={3} />
          </Form.Item>
          <Form.Item name="description" label="备注">
            <Input.TextArea placeholder="附加说明" rows={2} />
          </Form.Item>
        </Form>
      </Modal>

      {/* 字典多选弹窗 */}
      <Modal
        title={selectedDict ? `从「${selectedDict.name}」中选择` : '选择字典项'}
        open={selectorOpen}
        onOk={handleSelectorOk}
        onCancel={() => setSelectorOpen(false)}
        width={600}
        bodyStyle={{ padding: '12px 24px' }}
      >
        <Input.Search
          placeholder="搜索编码或名称"
          value={selectorSearch}
          onChange={(e) => setSelectorSearch(e.target.value)}
          style={{ marginBottom: 12 }}
          allowClear
        />
        <Table
          size="small"
          rowKey="id"
          rowSelection={{
            type: 'checkbox',
            selectedRowKeys,
            onChange: (keys) => setSelectedRowKeys(keys),
          }}
          columns={[
            { title: '编码', dataIndex: 'itemCode', width: 100 },
            { title: '名称', dataIndex: 'itemName' },
          ]}
          dataSource={selectorFilteredItems}
          pagination={false}
          scroll={{ y: 360 }}
          locale={{ emptyText: '无匹配项' }}
        />
        <div style={{ marginTop: 8, color: '#888', fontSize: 12, textAlign: 'right' }}>
          已选 {selectedRowKeys.length} 项 / 共 {selectorFilteredItems.length} 项
        </div>
      </Modal>
    </div>
  )
}
