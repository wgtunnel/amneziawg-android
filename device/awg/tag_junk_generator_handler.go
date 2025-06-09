package awg

import "fmt"

type TagJunkGeneratorHandler struct {
	generators []TagJunkGenerator
	length     int
	// Jc
	DefaultJunkCount int
}

func (handler *TagJunkGeneratorHandler) AppendGenerator(generators TagJunkGenerator) {
	handler.generators = append(handler.generators, generators)
	handler.length++
}

// validate that packets were defined consecutively
func (handler *TagJunkGeneratorHandler) Validate() error {
	seen := make([]bool, len(handler.generators))
	for _, generator := range handler.generators {
		index, err := generator.nameIndex()
		if index > len(handler.generators) {
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
	var rv = make([][]byte, handler.length+handler.DefaultJunkCount)
	for i, generator := range handler.generators {
		rv[i] = make([]byte, generator.packetSize)
		copy(rv[i], generator.generatePacket())
	}

	return rv
}
