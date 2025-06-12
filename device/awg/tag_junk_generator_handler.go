package awg

import "fmt"

type TagJunkGeneratorHandler struct {
	tagGenerators    []TagJunkGenerator
	length           int
	DefaultJunkCount int // Jc
}

func (handler *TagJunkGeneratorHandler) AppendGenerator(generators TagJunkGenerator) {
	handler.tagGenerators = append(handler.tagGenerators, generators)
	handler.length++
}

func (handler *TagJunkGeneratorHandler) IsDefined() bool {
	return len(handler.tagGenerators) > 0
}

// validate that packets were defined consecutively
func (handler *TagJunkGeneratorHandler) Validate() error {
	seen := make([]bool, len(handler.tagGenerators))
	for _, generator := range handler.tagGenerators {
		index, err := generator.nameIndex()
		if index > len(handler.tagGenerators) {
			return fmt.Errorf("junk packet index should be consecutive")
		}
		if err != nil {
			return fmt.Errorf("name index: %w", err)
		} else {
			seen[index-1] = true
		}
	}

	for _, found := range seen {
		if !found {
			return fmt.Errorf("junk packet index should be consecutive")
		}
	}

	return nil
}

func (handler *TagJunkGeneratorHandler) GeneratePackets() [][]byte {
	var rv = make([][]byte, 0, handler.length+handler.DefaultJunkCount)

	for i, tagGenerator := range handler.tagGenerators {
		PacketCounter.Inc()
		rv = append(rv, make([]byte, tagGenerator.packetSize))
		copy(rv[i], tagGenerator.generatePacket())
	}
	PacketCounter.Add(uint64(handler.DefaultJunkCount))

	return rv
}
