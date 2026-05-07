import React, { useState, useEffect, useRef, useMemo, useCallback } from 'react'
import { Layout, Table, Button, Modal, Form, Input, Select, Tag, message, Popconfirm, Card, Tree } from 'antd'
import { PlusOutlined, EditOutlined, DeleteOutlined, UploadOutlined, DownloadOutlined, FolderOutlined, FileOutlined } from '@ant-design/icons'
import axios from 'axios'

const { Sider, Content } = Layout
const DE_API = '/api/v1/data-elements'
const DS_API = '/api/v1/data-sets'
const DICT_API = '/api/v1/dictionaries'

function buildTreeData(dataSets) {
  const root = []
  const l1Map = new Map()

  dataSets.forEach(ds => {
    const l1Code = ds.catL1Code || ''
    const l1Name = ds.catL1Name || '未分类'
    const l2Code = ds.catL2Code || ''
    const l2Name = ds.catL2Name || '未分类'
    const l3Code = ds.catL3Code || ''
    const l3Name = ds.catL3Name || '未分类'

    const hasCategory = l1Code || l2Code || l3Code

    if (!hasCategory) {
      root.push({
        title: `${ds.name} (${ds.code})`,
        key: `ds-${ds.id}`,
        isLeaf: true,
        datasetId: ds.id,
        icon: <FileOutlined />,
      })
      return
    }

    // L1
    const l1Key = `cat-l1-${l1Code}`
    let l1Node = l1Map.get(l1Key)
    if (!l1Node) {
      l1Node = {
        title: l1Name,
        key: l1Key,
        icon: <FolderOutlined />,
        children: [],
      }
      root.push(l1Node)
      l1Map.set(l1Key, l1Node)
    }

    // L2
    const l2Key = `${l1Key}--l2-${l2Code || '_none'}`
    let l2Node = l1Node.children.find(n => n.key === l2Key)
    if (!l2Node) {
      l2Node = {
        title: l2Name,
        key: l2Key,
        icon: <FolderOutlined />,
        children: [],
      }
      l1Node.children.push(l2Node)
    }

    // L3
    const l3Key = `${l2Key}--l3-${l3Code || '_none'}`
    let l3Node = l2Node.children.find(n => n.key === l3Key)
    if (!l3Node) {
      l3Node = {
        title: l3Name,
        key: l3Key,
        icon: <FolderOutlined />,
        children: [],
      }
      l2Node.children.push(l3Node)
    }

    // DataSet leaf
    l3Node.children.push({
      title: ds.name,
      key: `ds-${ds.id}`,
      isLeaf: true,
      datasetId: ds.id,
      icon: <FileOutlined />,
    })
  })

  // 折叠冗余层级：如果某文件夹下只有一个同名的叶子节点，则去掉该文件夹
  const collapseRedundant = (nodes) => {
    const result = []
    for (const node of nodes) {
      if (node.isLeaf) {
        result.push(node)
        continue
      }
      const children = collapseRedundant(node.children)
      if (children.length === 1 && children[0].isLeaf && children[0].title === node.title) {
        result.push(children[0])
      } else {
        result.push({ ...node, children })
      }
    }
    return result
  }

  const collapsedRoot = collapseRedundant(root)

  // Sort by title
  const sortNodes = (nodes) => {
    nodes.sort((a, b) => (a.title || '').localeCompare(b.title || ''))
    nodes.forEach(n => {
      if (n.children) sortNodes(n.children)
    })
  }
  sortNodes(collapsedRoot)

  return collapsedRoot
}

export default function DataElementMgr() {
  const [dataSets, setDataSets] = useState([])
  const [dataElements, setDataElements] = useState([])
  const [dictionaries, setDictionaries] = useState([])
  const [selectedTreeKey, setSelectedTreeKey] = useState(null)
  const [selectedDsIds, setSelectedDsIds] = useState([])
  const [loading, setLoading] = useState(false)

  const [dsModalOpen, setDsModalOpen] = useState(false)
  const [editingDs, setEditingDs] = useState(null)
  const [dsForm] = Form.useForm()

  const [deModalOpen, setDeModalOpen] = useState(false)
  const [editingDe, setEditingDe] = useState(null)
  const [deForm] = Form.useForm()

  const [importModalOpen, setImportModalOpen] = useState(false)
  const [importLoading, setImportLoading] = useState(false)
  const [importResult, setImportResult] = useState(null)
  const fileInputRef = useRef(null)

  const [dePagination, setDePagination] = useState({ current: 1, pageSize: 20 })

  const treeData = useMemo(() => buildTreeData(dataSets), [dataSets])

  const nodeMap = useMemo(() => {
    const map = new Map()
    const walk = (nodes) => {
      for (const node of nodes) {
        map.set(node.key, node)
        if (node.children) walk(node.children)
      }
    }
    walk(treeData)
    return map
  }, [treeData])

  const selectedDsInfo = useMemo(() => {
    if (!selectedTreeKey || !selectedTreeKey.startsWith('ds-')) return null
    const dsId = Number(selectedTreeKey.replace('ds-', ''))
    return dataSets.find(ds => ds.id === dsId) || null
  }, [selectedTreeKey, dataSets])

  const handleTreeSelect = useCallback((selectedKeys) => {
    const key = selectedKeys[0]
    setSelectedTreeKey(key || null)

    if (!key) {
      setSelectedDsIds([])
      return
    }

    if (key.startsWith('ds-')) {
      const dsId = Number(key.replace('ds-', ''))
      setSelectedDsIds([dsId])
    } else {
      const node = nodeMap.get(key)
      if (node) {
        const ids = []
        const walk = (n) => {
          if (n.isLeaf && n.datasetId) ids.push(n.datasetId)
          if (n.children) n.children.forEach(walk)
        }
        walk(node)
        setSelectedDsIds(ids)
      } else {
        setSelectedDsIds([])
      }
    }
  }, [nodeMap])

  const fetchData = async () => {
    setLoading(true)
    try {
      const [dsRes, deRes, dictRes] = await Promise.all([
        axios.get(DS_API), axios.get(DE_API), axios.get(DICT_API)
      ])
      setDataSets(dsRes.data)
      setDataElements(deRes.data)
      setDictionaries(dictRes.data)
    } catch (e) {
      message.error('加载失败')
    }
    setLoading(false)
  }

  useEffect(() => { fetchData() }, [])

  // Auto select first dataset on initial load
  useEffect(() => {
    if (dataSets.length > 0 && !selectedTreeKey) {
      const firstDs = dataSets[0]
      const key = `ds-${firstDs.id}`
      setSelectedTreeKey(key)
      setSelectedDsIds([firstDs.id])
    }
  }, [dataSets, selectedTreeKey])

  // 切换树节点时重置数据元分页到第一页
  useEffect(() => {
    setDePagination(prev => ({ ...prev, current: 1 }))
  }, [selectedDsIds])

  const handleImport = async (file) => {
    if (!file) return
    setImportLoading(true)
    setImportResult(null)
    const formData = new FormData()
    formData.append('file', file)
    try {
      const res = await axios.post(`${DE_API}/import`, formData, {
        headers: { 'Content-Type': 'multipart/form-data' }
      })
      setImportResult(res.data)
      message.success(`导入完成: 数据元 ${res.data.dataElementCreated} 新建 / ${res.data.dataElementUpdated} 更新`)
      fetchData()
    } catch (e) {
      message.error('导入失败: ' + (e.response?.data?.message || e.message))
    }
    setImportLoading(false)
  }

  const downloadTemplate = () => {
    window.open(`${DE_API}/import-template`, '_blank')
  }

  const filteredElements = selectedDsIds.length > 0
    ? dataElements.filter(de => selectedDsIds.includes(de.datasetId))
    : dataElements

  // 数据集操作
  const handleSaveDs = async () => {
    const values = await dsForm.validateFields()
    try {
      if (editingDs) {
        await axios.put(`${DS_API}/${editingDs.id}`, { ...editingDs, ...values })
      } else {
        await axios.post(DS_API, values)
      }
      message.success('保存成功')
      setDsModalOpen(false)
      fetchData()
    } catch (e) {
      message.error('保存失败: ' + (e.response?.data?.message || e.message))
    }
  }

  const handleDeleteDs = async (id) => {
    try {
      await axios.delete(`${DS_API}/${id}`)
      message.success('删除成功')
      if (selectedDsInfo?.id === id) {
        setSelectedTreeKey(null)
        setSelectedDsIds([])
      }
      fetchData()
    } catch (e) {
      message.error(e.response?.data?.message || '删除失败')
    }
  }

  // 数据元操作
  const handleSaveDe = async () => {
    const values = await deForm.validateFields()
    try {
      const payload = { ...values, datasetId: selectedDsInfo?.id }
      if (editingDe) {
        await axios.put(`${DE_API}/${editingDe.id}`, { ...editingDe, ...payload })
      } else {
        await axios.post(DE_API, payload)
      }
      message.success('保存成功')
      setDeModalOpen(false)
      fetchData()
    } catch (e) {
      message.error('保存失败: ' + (e.response?.data?.message || e.message))
    }
  }

  const handleDeleteDe = async (id) => {
    try {
      await axios.delete(`${DE_API}/${id}`)
      message.success('删除成功')
      fetchData()
    } catch (e) {
      message.error(e.response?.data?.message || '删除失败')
    }
  }

  const deColumns = [
    { title: 'ID', dataIndex: 'id', width: 50 },
    { title: '编码', dataIndex: 'code' },
    { title: '名称', dataIndex: 'name' },
    { title: '标准编码', dataIndex: 'standardCode', width: 140 },
    { title: '英文名', dataIndex: 'englishName', width: 160 },
    { title: '驼峰名', dataIndex: 'camelName', width: 160 },
    { title: '数据类型', dataIndex: 'dataType', width: 100, render: (v) => (
      <Tag color={v === 'NUMERIC' ? 'blue' : v === 'STRING' ? 'green' : 'orange'}>{v}</Tag>
    )},
    { title: '字典', dataIndex: 'dictCode', width: 120 },
    { title: '定义', dataIndex: 'definition', ellipsis: true },
    {
      title: '操作', width: 120,
      render: (_, record) => (
        <>
          <Button type="link" size="small" icon={<EditOutlined />} onClick={() => {
            setEditingDe(record)
            deForm.setFieldsValue(record)
            setDeModalOpen(true)
          }}>编辑</Button>
          <Popconfirm title="确认删除?" onConfirm={() => handleDeleteDe(record.id)}>
            <Button type="link" size="small" danger icon={<DeleteOutlined />}>删除</Button>
          </Popconfirm>
        </>
      )
    }
  ]

  return (
    <div style={{ padding: 24 }}>
      <h2>数据集与数据元管理</h2>

      <Layout style={{ minHeight: 500, background: '#fff' }}>
        {/* 左侧分类树 */}
        <Sider width={300} style={{ background: '#fafafa', borderRight: '1px solid #f0f0f0', padding: 12 }}>
          <Button
            type="primary"
            size="small"
            icon={<PlusOutlined />}
            block
            style={{ marginBottom: 12 }}
            onClick={() => {
              setEditingDs(null)
              dsForm.resetFields()
              setDsModalOpen(true)
            }}
          >
            新增数据集
          </Button>
          <Tree
            treeData={treeData}
            selectedKeys={selectedTreeKey ? [selectedTreeKey] : []}
            onSelect={handleTreeSelect}
            showIcon
            defaultExpandAll
          />
        </Sider>

        {/* 右侧内容 */}
        <Content style={{ padding: '0 16px' }}>
          {/* 选中节点信息卡片 */}
          {selectedDsInfo ? (
            <Card
              size="small"
              style={{ marginBottom: 16 }}
              title={
                <span>
                  <FileOutlined style={{ marginRight: 8 }} />
                  数据集: {selectedDsInfo.name}
                  {selectedDsInfo.englishName && <span style={{ color: '#888', marginLeft: 8 }}>({selectedDsInfo.englishName})</span>}
                </span>
              }
              extra={
                <div style={{ display: 'flex', gap: 8 }}>
                  <Button size="small" icon={<EditOutlined />} onClick={() => {
                    setEditingDs(selectedDsInfo)
                    dsForm.setFieldsValue(selectedDsInfo)
                    setDsModalOpen(true)
                  }}>编辑</Button>
                  <Popconfirm title="确认删除?" onConfirm={() => handleDeleteDs(selectedDsInfo.id)}>
                    <Button size="small" danger icon={<DeleteOutlined />}>删除</Button>
                  </Popconfirm>
                </div>
              }
            >
              <div style={{ display: 'flex', gap: 24, flexWrap: 'wrap' }}>
                <span><strong>编码:</strong> {selectedDsInfo.code}</span>
                <span><strong>分类:</strong> {selectedDsInfo.catL1Name || '-'} / {selectedDsInfo.catL2Name || '-'} / {selectedDsInfo.catL3Name || '-'}</span>
                <span><strong>描述:</strong> {selectedDsInfo.description || '-'}</span>
              </div>
            </Card>
          ) : selectedTreeKey ? (
            <Card size="small" style={{ marginBottom: 16 }}>
              <FolderOutlined style={{ marginRight: 8 }} />
              已选择分类，共包含 {selectedDsIds.length} 个数据集，{filteredElements.length} 个数据元
            </Card>
          ) : null}

          {/* 数据元管理 */}
          <Card
            title="数据元"
            size="small"
            extra={
              <div style={{ display: 'flex', gap: 8 }}>
                <Button type="primary" size="small" icon={<PlusOutlined />} disabled={!selectedDsInfo} onClick={() => {
                  setEditingDe(null)
                  deForm.resetFields()
                  setDeModalOpen(true)
                }}>新增数据元</Button>
                <Button size="small" icon={<UploadOutlined />} onClick={() => {
                  setImportResult(null)
                  setImportModalOpen(true)
                }}>批量导入</Button>
              </div>
            }
          >
            <Table rowKey="id" size="small" columns={deColumns} dataSource={filteredElements} loading={loading} bordered
              pagination={{
                ...dePagination,
                showSizeChanger: true,
                pageSizeOptions: ['10', '20', '50', '100'],
                showTotal: (total) => `共 ${total} 条`,
                onChange: (page, pageSize) => setDePagination({ current: page, pageSize }),
              }}
            />
          </Card>
        </Content>
      </Layout>

      {/* 数据集弹窗 */}
      <Modal title={editingDs ? '编辑数据集' : '新增数据集'} open={dsModalOpen} onOk={handleSaveDs} onCancel={() => setDsModalOpen(false)}>
        <Form form={dsForm} layout="vertical">
          <Form.Item name="code" label="编码" rules={[{ required: true }]}>
            <Input disabled={!!editingDs} />
          </Form.Item>
          <Form.Item name="name" label="名称" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="englishName" label="英文名">
            <Input />
          </Form.Item>
          <Form.Item name="catL1Code" label="一级分类编码">
            <Input />
          </Form.Item>
          <Form.Item name="catL1Name" label="一级分类名称">
            <Input />
          </Form.Item>
          <Form.Item name="catL2Code" label="二级分类编码">
            <Input />
          </Form.Item>
          <Form.Item name="catL2Name" label="二级分类名称">
            <Input />
          </Form.Item>
          <Form.Item name="catL3Code" label="三级分类编码">
            <Input />
          </Form.Item>
          <Form.Item name="catL3Name" label="三级分类名称">
            <Input />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <Input.TextArea rows={2} />
          </Form.Item>
        </Form>
      </Modal>

      {/* 数据元弹窗 */}
      <Modal title={editingDe ? '编辑数据元' : '新增数据元'} open={deModalOpen} onOk={handleSaveDe} onCancel={() => setDeModalOpen(false)}>
        <Form form={deForm} layout="vertical">
          <Form.Item name="code" label="编码" rules={[{ required: true }]}>
            <Input disabled={!!editingDe} />
          </Form.Item>
          <Form.Item name="name" label="名称" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="standardCode" label="标准编码">
            <Input />
          </Form.Item>
          <Form.Item name="englishName" label="英文名">
            <Input />
          </Form.Item>
          <Form.Item name="camelName" label="驼峰名称">
            <Input />
          </Form.Item>
          <Form.Item name="dataType" label="数据类型" rules={[{ required: true }]}>
            <Select placeholder="选择数据类型">
              <Select.Option value="STRING">字符串</Select.Option>
              <Select.Option value="NUMERIC">数值</Select.Option>
              <Select.Option value="DICTIONARY">字典</Select.Option>
              <Select.Option value="DICTIONARY_SET">字典集合</Select.Option>
              <Select.Option value="BOOLEAN">布尔</Select.Option>
              <Select.Option value="DATE_TIME">日期时间</Select.Option>
              <Select.Option value="LONG_TEXT">长文本</Select.Option>
              <Select.Option value="STRING_LIST">字符串集合</Select.Option>
            </Select>
          </Form.Item>
          <Form.Item name="dictCode" label="关联字典">
            <Select placeholder="选择字典" allowClear>
              {dictionaries.map(d => <Select.Option key={d.code} value={d.code}>{d.name}</Select.Option>)}
            </Select>
          </Form.Item>
          <Form.Item name="definition" label="定义">
            <Input.TextArea rows={2} />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <Input.TextArea rows={2} />
          </Form.Item>
        </Form>
      </Modal>

      {/* 批量导入弹窗 */}
      <Modal
        title="批量导入数据元"
        open={importModalOpen}
        onCancel={() => setImportModalOpen(false)}
        footer={[
          <Button key="template" icon={<DownloadOutlined />} onClick={downloadTemplate}>下载模板</Button>,
          <Button key="close" onClick={() => setImportModalOpen(false)}>关闭</Button>,
        ]}
      >
        <div style={{ marginBottom: 16 }}>
          <input
            type="file"
            accept=".xlsx"
            ref={fileInputRef}
            style={{ display: 'none' }}
            onChange={(e) => {
              const file = e.target.files?.[0]
              if (file) handleImport(file)
              e.target.value = ''
            }}
          />
          <Button
            type="primary"
            icon={<UploadOutlined />}
            loading={importLoading}
            onClick={() => fileInputRef.current?.click()}
            block
          >
            选择 Excel 文件 (.xlsx)
          </Button>
        </div>
        {importResult && (
          <div style={{ background: '#f6ffed', border: '1px solid #b7eb8f', padding: 12, borderRadius: 4 }}>
            <p><strong>导入结果</strong></p>
            <p>总行数: {importResult.totalRows}</p>
            <p>数据集: {importResult.dataSetCreated} 新建 / {importResult.dataSetUpdated} 更新</p>
            <p>数据元: {importResult.dataElementCreated} 新建 / {importResult.dataElementUpdated} 更新</p>
            <p>自动条件模型: {importResult.conditionModelCreated} 个</p>
            {importResult.errors?.length > 0 && (
              <div style={{ marginTop: 8 }}>
                <p style={{ color: '#cf1322' }}>错误 ({importResult.errors.length} 条):</p>
                <Table
                  size="small"
                  dataSource={importResult.errors}
                  columns={[
                    { title: '行号', dataIndex: 'row', width: 60 },
                    { title: '标准编码', dataIndex: 'standardCode' },
                    { title: '驼峰名', dataIndex: 'camelName' },
                    { title: '错误', dataIndex: 'message' },
                  ]}
                  pagination={false}
                  rowKey={(r, i) => i}
                />
              </div>
            )}
          </div>
        )}
      </Modal>
    </div>
  )
}
